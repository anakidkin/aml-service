package io.github.anakidkin.aml.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEvaluationServiceImplTest {

  @Mock
  private CassandraHistoryRepository cassandraHistoryRepository;

  @Mock
  private CassandraAccountCounterpartyRepository cassandraAccountCounterpartyRepository;

  @Mock
  private JpaTransactionRepository jpaTransactionRepository;

  @Mock
  private JpaOutboxRepository jpaOutboxRepository;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private TransactionDomainMapper transactionDomainMapper;

  @Mock
  private RedissonClient redisson;

  @Mock
  private AccountVolumeCache volumeCache;

  @Mock
  private RLock lock;

  @Mock
  private AmlRule rule1;

  @Mock
  private AmlRule rule2;

  @Captor
  private ArgumentCaptor<OutboxEventEntity> outboxCaptor;

  private TransactionEvaluationServiceImpl evaluationService;

  @BeforeEach
  void setUp() {
    when(rule1.getPriority()).thenReturn(1);
    when(rule2.getPriority()).thenReturn(2);

    when(redisson.getLock(anyString())).thenReturn(lock);
    doNothing().when(lock).lock(anyLong(), any(TimeUnit.class));

    evaluationService = new TransactionEvaluationServiceImpl(
        List.of(rule2, rule1),
        cassandraHistoryRepository,
        cassandraAccountCounterpartyRepository,
        jpaTransactionRepository,
        jpaOutboxRepository,
        objectMapper,
        transactionDomainMapper,
        redisson,
        volumeCache
    );
  }

  @Test
  @DisplayName("Should REJECT transaction and set CRITICAL risk level when any rule returns BLOCKED")
  void shouldRejectWhenRuleReturnsBlocked() throws Exception {
    Transaction tx = createTransaction();
    RuleResult resultBlocked = new RuleResult("RULE_1", 1, RuleStatus.BLOCKED, "Blocked reason", 5, false);

    when(rule1.evaluate(eq(tx), any(AccountContext.class))).thenReturn(resultBlocked);
    when(rule2.evaluate(eq(tx), any(AccountContext.class))).thenReturn(
        new RuleResult("RULE_2", 1, RuleStatus.PASSED, "OK", 2, false)
    );
    when(transactionDomainMapper.toEntity(any())).thenReturn(new TransactionEntity());
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"status\":\"REJECTED\"}");

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.REJECTED);
    assertThat(result.riskAssessment().level()).isEqualTo(RiskLevel.CRITICAL);
    assertThat(result.riskAssessment().score()).isEqualTo(100.0);

    verify(jpaTransactionRepository).save(any(TransactionEntity.class));
    verify(jpaOutboxRepository).save(outboxCaptor.capture());

    assertThat(outboxCaptor.getValue().getAggregateId()).isEqualTo(tx.id());
  }

  @Test
  @DisplayName("Should FLAG transaction and set HIGH risk level when rule returns FLAGGED with isHard=true")
  void shouldFlagWhenRuleReturnsHardFlagged() throws Exception {
    Transaction tx = createTransaction();
    RuleResult resultHardFlag = new RuleResult("RULE_1", 1, RuleStatus.FLAGGED, "Hard flag", 5, true);

    when(rule1.evaluate(eq(tx), any(AccountContext.class))).thenReturn(resultHardFlag);
    when(rule2.evaluate(eq(tx), any(AccountContext.class))).thenReturn(
        new RuleResult("RULE_2", 1, RuleStatus.PASSED, "OK", 2, false)
    );
    when(transactionDomainMapper.toEntity(any())).thenReturn(new TransactionEntity());
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.FLAGGED);
    assertThat(result.riskAssessment().level()).isEqualTo(RiskLevel.HIGH);
    assertThat(result.riskAssessment().score()).isEqualTo(95.0);
  }

  @Test
  @DisplayName("Should FLAG transaction when 2 or more soft FLAGGED rules triggered")
  void shouldFlagWhenMultipleSoftFlagsTriggered() throws Exception {
    Transaction tx = createTransaction();
    RuleResult flag1 = new RuleResult("RULE_1", 1, RuleStatus.FLAGGED, "Soft flag 1", 5, false);
    RuleResult flag2 = new RuleResult("RULE_2", 1, RuleStatus.FLAGGED, "Soft flag 2", 3, false);

    when(rule1.evaluate(eq(tx), any(AccountContext.class))).thenReturn(flag1);
    when(rule2.evaluate(eq(tx), any(AccountContext.class))).thenReturn(flag2);
    when(transactionDomainMapper.toEntity(any())).thenReturn(new TransactionEntity());
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.FLAGGED);
    assertThat(result.riskAssessment().level()).isEqualTo(RiskLevel.MEDIUM);
    assertThat(result.riskAssessment().score()).isEqualTo(70.0); // 2 * 35.0
  }

  @Test
  @DisplayName("Should APPROVE transaction and set LOW risk when all rules pass")
  void shouldApproveWhenAllRulesPass() throws Exception {
    Transaction tx = createTransaction();
    RuleResult pass1 = new RuleResult("RULE_1", 1, RuleStatus.PASSED, "OK", 2, false);
    RuleResult pass2 = new RuleResult("RULE_2", 1, RuleStatus.PASSED, "OK", 1, false);

    when(rule1.evaluate(eq(tx), any(AccountContext.class))).thenReturn(pass1);
    when(rule2.evaluate(eq(tx), any(AccountContext.class))).thenReturn(pass2);
    when(transactionDomainMapper.toEntity(any())).thenReturn(new TransactionEntity());
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.APPROVED);
    assertThat(result.riskAssessment().level()).isEqualTo(RiskLevel.LOW);
    assertThat(result.riskAssessment().score()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when ObjectMapper serialization fails")
  void shouldThrowIllegalStateExceptionOnJsonProcessingError() throws Exception {
    Transaction tx = createTransaction();
    when(rule1.evaluate(eq(tx), any(AccountContext.class))).thenReturn(
        new RuleResult("RULE_1", 1, RuleStatus.PASSED, "OK", 1, false)
    );
    when(rule2.evaluate(eq(tx), any(AccountContext.class))).thenReturn(
        new RuleResult("RULE_2", 1, RuleStatus.PASSED, "OK", 1, false)
    );
    when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {
    });

    assertThatThrownBy(() -> evaluationService.evaluate(tx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to serialize transaction payload for outbox");
  }

  private Transaction createTransaction() {
    return new Transaction(
        UUID.randomUUID(),
        "ACC_1001",
        "ACC_2002",
        new Money(new BigDecimal("500.00"), "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );
  }
}