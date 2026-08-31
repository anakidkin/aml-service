package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RiskAssessment;
import io.github.anakidkin.aml.domain.RiskLevel;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.service.AccountContextService;
import io.github.anakidkin.aml.service.OutboxService;
import io.github.anakidkin.aml.service.RuleEngineService;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import io.micrometer.core.annotation.Timed;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEvaluationServiceImpl implements TransactionEvaluationService {

  private static final String REDIS_LOCK_KET_PREFIX = "lock:acc:";

  private final RuleEngineService ruleEngineService;
  private final AccountContextService accountContextService;
  private final OutboxService outboxService;
  private final RedissonClient redisson;
  private final AccountVolumeCache volumeCache;

  @Override
  @Timed(
      value = "aml.transaction.evaluation.latency",
      description = "Full synchronous pipeline evaluation latency",
      percentiles = {0.50, 0.95, 0.99, 0.999})
  public Transaction evaluate(Transaction transaction) {
    log.info("Processing on thread: {}", Thread.currentThread());
    String lockKey = REDIS_LOCK_KET_PREFIX + transaction.accountFrom();
    RLock lock = redisson.getLock(lockKey);
    boolean acquired = false;

    try {
      acquired = lock.tryLock(3, 2, TimeUnit.SECONDS);
      if (!acquired) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Account lock timeout");
      }
      log.debug("Lock acquired for {}", transaction.accountFrom());

      AccountContext context = accountContextService.buildAccountContext(transaction);
      List<RuleResult> ruleResults = ruleEngineService.evaluate(transaction, context);
      Transaction processedTransaction = processTransaction(transaction, ruleResults);

      Transaction savedTransaction = outboxService.save(processedTransaction, ruleResults);

      if (savedTransaction.status() != TransactionStatus.REJECTED) {
        try {
          volumeCache.addTransaction(
              transaction.accountFrom(),
              transaction.accountTo(),
              transaction.money().amount().doubleValue(),
              transaction.createdAt());
        } catch (Exception e) {
          log.error("Failed to update volume cache for account {}", transaction.accountFrom(), e);
        }
      }

      return savedTransaction;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Thread was interrupted while acquiring lock", e);
    } finally {
      if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("Unlock acquired for {}", transaction.accountFrom());
      }
    }
  }

  private Transaction processTransaction(Transaction transaction, List<RuleResult> ruleResults) {
    boolean hasBlock = ruleResults.stream().anyMatch(r -> r.status() == RuleStatus.BLOCKED);

    boolean hasHardBlockRule =
        ruleResults.stream().anyMatch(r -> r.status() == RuleStatus.FLAGGED && r.isHard());

    long flaggedCount = ruleResults.stream().filter(r -> r.status() == RuleStatus.FLAGGED).count();

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
        Instant.now());
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

  /** Calculates the combined risk score for transaction */
  private double calculateRiskScore(boolean hasBlock, boolean hasHardBlock, long flaggedCount) {
    if (hasBlock) {
      return 100.0;
    }
    if (hasHardBlock) {
      return 95.0;
    }
    return Math.min(100.0, flaggedCount * 35.0);
  }
}
