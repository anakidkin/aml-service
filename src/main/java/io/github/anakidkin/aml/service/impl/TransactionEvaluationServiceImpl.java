package io.github.anakidkin.aml.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RiskAssessment;
import io.github.anakidkin.aml.domain.RiskLevel;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import io.github.anakidkin.aml.entity.TransactionEntity;
import io.github.anakidkin.aml.mapper.TransactionDomainMapper;
import io.github.anakidkin.aml.repository.CassandraAccountCounterpartyRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import io.github.anakidkin.aml.rules.AmlRule;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TransactionEvaluationServiceImpl implements TransactionEvaluationService {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private static final String REDIS_LOCK_KET_PREFIX = "lock:acc:";

  private final List<AmlRule> amlRules;
  private final CassandraHistoryRepository cassandraHistoryRepository;
  private final CassandraAccountCounterpartyRepository cassandraAccountCounterpartyRepository;
  private final JpaTransactionRepository jpaTransactionRepository;
  private final JpaOutboxRepository jpaOutboxRepository;
  private final ObjectMapper objectMapper;
  private final TransactionDomainMapper transactionDomainMapper;
  private final RedissonClient redisson;
  private final AccountVolumeCache volumeCache;


  public TransactionEvaluationServiceImpl(
      List<AmlRule> amlRules,
      CassandraHistoryRepository cassandraHistoryRepository,
      CassandraAccountCounterpartyRepository cassandraAccountCounterpartyRepository,
      JpaTransactionRepository jpaTransactionRepository,
      JpaOutboxRepository jpaOutboxRepository,
      ObjectMapper objectMapper,
      TransactionDomainMapper transactionDomainMapper,
      RedissonClient redisson,
      AccountVolumeCache volumeCache
  ) {
    this.amlRules = amlRules.stream()
        .sorted(Comparator.comparingInt(AmlRule::getPriority))
        .toList();
    this.cassandraHistoryRepository = cassandraHistoryRepository;
    this.cassandraAccountCounterpartyRepository = cassandraAccountCounterpartyRepository;
    this.jpaTransactionRepository = jpaTransactionRepository;
    this.jpaOutboxRepository = jpaOutboxRepository;
    this.objectMapper = objectMapper;
    this.transactionDomainMapper = transactionDomainMapper;
    this.redisson = redisson;
    this.volumeCache = volumeCache;
  }

  @Override
  @Transactional
  @Timed(
      value = "aml.transaction.evaluation.latency",
      description = "Full synchronous pipeline evaluation latency",
      percentiles = {0.50, 0.95, 0.99, 0.999}
  )
  public Transaction evaluate(Transaction transaction) {
    String lockKey = REDIS_LOCK_KET_PREFIX + transaction.accountFrom();
    RLock lock = redisson.getLock(lockKey);
    try {
      lock.lock(2, TimeUnit.SECONDS);
      log.debug("Lock acquired for {}", transaction.accountFrom());
      AccountContext context = buildAccountContext(transaction);

      List<RuleResult> ruleResults = amlRules.stream()
          .map(rule -> rule.evaluate(transaction, context))
          .toList();

      Transaction processedTransaction = procesTransaction(transaction, ruleResults);

      TransactionEntity entity = transactionDomainMapper.toEntity(processedTransaction);
      jpaTransactionRepository.save(entity);

      saveOutboxEvent(processedTransaction, ruleResults);

      if (entity.getStatus() != TransactionStatus.REJECTED) {
        volumeCache.addAmount(transaction.accountFrom(), transaction.money().amount().doubleValue(), transaction.createdAt());
      }

      return processedTransaction;
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("Unlock acquired for {}", transaction.accountFrom());
      }
    }
  }

  private AccountContext buildAccountContext(Transaction transaction) {
    Instant txTime = transaction.createdAt();
    Instant last24Hours = txTime.minus(24, ChronoUnit.HOURS);
    Instant last30Days = txTime.minus(30, ChronoUnit.DAYS);

    double volume24h = volumeCache.get24hVolume(transaction.accountFrom(), txTime);

    long txCount24h = cassandraHistoryRepository.countTransactionsInWindow(
        transaction.accountFrom(), last24Hours, txTime
    );

    String dateWindow = DATE_FORMATTER.format(txTime);
    int uniqueCounterparties24h = cassandraAccountCounterpartyRepository.countUniqueCounterparties(
        transaction.accountTo(), dateWindow
    );
    BigDecimal total30dVal = cassandraHistoryRepository.sumAmount(
        transaction.accountFrom(), last30Days, txTime
    );
    BigDecimal p2p30dVal = cassandraHistoryRepository.sumP2pAmount(
        transaction.accountFrom(), last30Days, txTime
    );
    double p2pRatio30d = 0.0;
    if (total30dVal != null && total30dVal.compareTo(BigDecimal.ZERO) > 0) {
      double total30d = total30dVal.doubleValue();
      double p2p30d = (p2p30dVal != null) ? p2p30dVal.doubleValue() : 0.0;
      p2pRatio30d = p2p30d / total30d;
    }


    long txCount30d = cassandraHistoryRepository.countTransactionsInWindow(
        transaction.accountFrom(), last30Days, txTime
    );

    return new AccountContext(
        volume24h,
        txCount24h,
        uniqueCounterparties24h,
        p2pRatio30d,
        txCount30d == 0
    );
  }

  private Transaction procesTransaction(Transaction transaction, List<RuleResult> ruleResults) {
    boolean hasBlock = ruleResults.stream()
        .anyMatch(r -> r.status() == RuleStatus.BLOCKED);

    boolean hasHardBlockRule = ruleResults.stream()
        .anyMatch(r -> r.status() == RuleStatus.FLAGGED && r.isHard());

    long flaggedCount = ruleResults.stream()
        .filter(r -> r.status() == RuleStatus.FLAGGED)
        .count();

    TransactionStatus status;
    if (hasBlock) {
      status = TransactionStatus.REJECTED;
    } else if (hasHardBlockRule || flaggedCount >= 2) {
      status = TransactionStatus.FLAGGED;
    } else {
      status = TransactionStatus.APPROVED;
    }

    RiskLevel riskLevel = calculateRiskLevel(hasBlock, hasHardBlockRule, flaggedCount);
    double riskScore = calculateRiskScore(hasBlock, hasHardBlockRule, flaggedCount);

    RiskAssessment riskAssessment = new RiskAssessment(riskScore, riskLevel, ruleResults);

    return new Transaction(
        transaction.id(),
        transaction.accountFrom(),
        transaction.accountTo(),
        transaction.money(),
        transaction.mccCode(),
        transaction.isP2p(),
        status,
        riskAssessment,
        transaction.createdAt(),
        Instant.now()
    );
  }

  private RiskLevel calculateRiskLevel(boolean hasBlock, boolean hasHardBlock, long flaggedCount) {
    if (hasBlock) {
      return RiskLevel.CRITICAL;
    }
    if (hasHardBlock || flaggedCount >= 3) {
      return RiskLevel.HIGH;
    }
    if (flaggedCount > 0) {
      return RiskLevel.MEDIUM;
    }
    return RiskLevel.LOW;
  }

  /**
   * Calculates the combined risk score for transaction
   */
  private double calculateRiskScore(boolean hasBlock, boolean hasHardBlock, long flaggedCount) {
    if (hasBlock) {
      return 100.0;
    }
    if (hasHardBlock) {
      return 95.0;
    }
    return Math.min(100.0, flaggedCount * 35.0);
  }

  private void saveOutboxEvent(Transaction tx, List<RuleResult> ruleResults) {
    var event = transactionDomainMapper.toEvaluatedEvent(tx, ruleResults);
    try {
      String payloadJson = objectMapper.writeValueAsString(event);

      OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
          .aggregateId(tx.id())
          .aggregateType("transaction")
          .eventType("TRANSACTION_EVALUATED")
          .payload(payloadJson)
          .build();

      jpaOutboxRepository.save(outboxEvent);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize transaction payload for outbox", e);
    }
  }


}