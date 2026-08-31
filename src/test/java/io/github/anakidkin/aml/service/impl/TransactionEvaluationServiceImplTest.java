package io.github.anakidkin.aml.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.service.AccountContextService;
import io.github.anakidkin.aml.service.OutboxService;
import io.github.anakidkin.aml.service.RuleEngineService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransactionEvaluationServiceImplTest {

  @Mock private RuleEngineService ruleEngineService;

  @Mock private AccountContextService accountContextService;

  @Mock private OutboxService outboxService;

  @Mock private RedissonClient redisson;

  @Mock private AccountVolumeCache volumeCache;

  @Mock private RLock lock;

  @InjectMocks private TransactionEvaluationServiceImpl evaluationService;

  @BeforeEach
  void setUp() {
    when(redisson.getLock(anyString())).thenReturn(lock);
  }

  private void mockSuccessfulLock() throws InterruptedException {
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    when(lock.isHeldByCurrentThread()).thenReturn(true);
  }

  @Test
  @DisplayName(
      "Should evaluate rules, save transaction and update volume cache when status is APPROVED")
  void shouldEvaluateAndSaveApprovedTransaction() throws InterruptedException {
    mockSuccessfulLock();
    Transaction tx = createTransaction(TransactionStatus.PENDING);
    AccountContext mockContext = createAccountContext();
    List<RuleResult> mockRuleResults = List.of();
    Transaction processedTx = createTransaction(TransactionStatus.APPROVED);

    when(accountContextService.buildAccountContext(tx)).thenReturn(mockContext);
    when(ruleEngineService.evaluate(tx, mockContext)).thenReturn(mockRuleResults);
    when(outboxService.save(any(), eq(mockRuleResults))).thenReturn(processedTx);

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.APPROVED);

    verify(redisson).getLock("lock:acc:" + tx.accountFrom());
    verify(accountContextService).buildAccountContext(tx);
    verify(ruleEngineService).evaluate(tx, mockContext);
    verify(outboxService).save(any(), eq(mockRuleResults));
    verify(volumeCache)
        .addTransaction(
            tx.accountFrom(), tx.accountTo(), tx.money().amount().doubleValue(), tx.createdAt());
    verify(lock).unlock();
  }

  @Test
  @DisplayName("Should NOT update volume cache when transaction status is REJECTED")
  void shouldNotUpdateVolumeCacheWhenRejected() throws InterruptedException {
    mockSuccessfulLock();
    Transaction tx = createTransaction(TransactionStatus.PENDING);
    Transaction rejectedTx = createTransaction(TransactionStatus.REJECTED);

    when(accountContextService.buildAccountContext(tx)).thenReturn(createAccountContext());
    when(ruleEngineService.evaluate(any(), any())).thenReturn(List.of());
    when(outboxService.save(any(), any())).thenReturn(rejectedTx);

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.REJECTED);
    verify(volumeCache, never()).addTransaction(anyString(), anyString(), anyDouble(), any());
    verify(lock).unlock();
  }

  @Test
  @DisplayName("Should throw ResponseStatusException and not proceed when Redis lock fails")
  void shouldThrowExceptionWhenLockAcquisitionFails() throws InterruptedException {
    Transaction tx = createTransaction(TransactionStatus.PENDING);
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

    assertThatThrownBy(() -> evaluationService.evaluate(tx))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Account lock timeout");

    verify(accountContextService, never()).buildAccountContext(any());
    verify(ruleEngineService, never()).evaluate(any(), any());
    verify(outboxService, never()).save(any(), any());
    verify(lock, never()).unlock();
  }

  @Test
  @DisplayName("Should ensure unlock is always called even if processing fails with exception")
  void shouldAlwaysReleaseLockOnException() throws InterruptedException {
    mockSuccessfulLock();
    Transaction tx = createTransaction(TransactionStatus.PENDING);
    when(accountContextService.buildAccountContext(tx))
        .thenThrow(new RuntimeException("Cassandra timeout"));

    assertThatThrownBy(() -> evaluationService.evaluate(tx))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Cassandra timeout");

    verify(outboxService, never()).save(any(), any());
    verify(lock).unlock();
  }

  @Test
  @DisplayName("Should catch volumeCache exception and return transaction successfully")
  void shouldGracefullyHandleVolumeCacheFailure() throws InterruptedException {
    mockSuccessfulLock();
    Transaction tx = createTransaction(TransactionStatus.PENDING);
    Transaction approvedTx = createTransaction(TransactionStatus.APPROVED);

    when(accountContextService.buildAccountContext(tx)).thenReturn(createAccountContext());
    when(outboxService.save(any(), any())).thenReturn(approvedTx);
    doThrow(new RuntimeException("Redis down"))
        .when(volumeCache)
        .addTransaction(anyString(), anyString(), anyDouble(), any());

    Transaction result = evaluationService.evaluate(tx);

    assertThat(result.status()).isEqualTo(TransactionStatus.APPROVED);
    verify(lock).unlock();
  }

  private AccountContext createAccountContext() {
    return new AccountContext(1000.0, 5, 2, 0.8, false);
  }

  private Transaction createTransaction(TransactionStatus status) {
    return new Transaction(
        UUID.randomUUID(),
        "ACC_1001",
        "ACC_2002",
        new Money(new BigDecimal("500.00"), "USD"),
        "5411",
        false,
        status,
        null,
        Instant.now(),
        Instant.now());
  }
}
