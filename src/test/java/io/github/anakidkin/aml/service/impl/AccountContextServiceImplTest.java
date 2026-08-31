package io.github.anakidkin.aml.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.cache.Metrics24h;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountContextServiceImplTest {

  @Mock private CassandraHistoryRepository cassandraHistoryRepository;

  @Mock private AccountVolumeCache volumeCache;

  @InjectMocks private AccountContextServiceImpl accountContextService;

  @Test
  @DisplayName(
      "Should correctly calculate all metrics when history exists and skip Cassandra 30d count query")
  void shouldBuildAccountContextCorrectly() {
    Instant txTime = Instant.parse("2026-03-01T10:00:00Z");
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    Instant expected30dAgo = txTime.minus(30, ChronoUnit.DAYS);

    // Valkey/Redis metrics for 24h
    when(volumeCache.get24hMetrics("ACC_FROM", txTime))
        .thenReturn(new Metrics24h(1500.0, 5L, true));
    when(volumeCache.getUniqueCounterparties24h("ACC_TO")).thenReturn(3);

    // Cassandra metrics for 30d
    when(cassandraHistoryRepository.sumAmount("ACC_FROM", expected30dAgo, txTime))
        .thenReturn(new BigDecimal("1000.00"));
    when(cassandraHistoryRepository.sumP2pAmount("ACC_FROM", expected30dAgo, txTime))
        .thenReturn(new BigDecimal("400.00"));

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.volume24h()).isEqualTo(1500.0);
    assertThat(context.txCount24h()).isEqualTo(5L);
    assertThat(context.uniqueCounterparties24h()).isEqualTo(3);
    assertThat(context.p2pRatio30d()).isEqualTo(0.4); // 400.00 / 1000.00
    assertThat(context.isDormantAccount()).isFalse(); // txCount24h > 0 -> guarantees active account

    // Short-circuit check: Cassandra tx count for 30d should NOT be called when txCount24h > 0
    verify(cassandraHistoryRepository, never()).countTransactionsInWindow(any(), any(), any());
  }

  @Test
  @DisplayName("Should query Cassandra for 30d tx count when account has 0 transactions in 24h")
  void shouldQueryCassandraWhenInactiveIn24h() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(eq("ACC_FROM"), any(Instant.class)))
        .thenReturn(new Metrics24h(0.0, 0L, false));
    when(volumeCache.getUniqueCounterparties24h("ACC_TO")).thenReturn(0);

    when(cassandraHistoryRepository.countTransactionsInWindow(eq("ACC_FROM"), any(), any()))
        .thenReturn(0L);
    when(cassandraHistoryRepository.sumAmount(any(), any(), any())).thenReturn(BigDecimal.ZERO);

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.volume24h()).isEqualTo(0.0);
    assertThat(context.txCount24h()).isZero();
    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
    assertThat(context.isDormantAccount()).isTrue(); // txCount30d == 0

    // Cassandra MUST be checked when 24h tx count is 0
    verify(cassandraHistoryRepository).countTransactionsInWindow(eq("ACC_FROM"), any(), any());
  }

  @Test
  @DisplayName("Should handle zero or null total 30d volume and set p2pRatio to 0.0")
  void shouldHandleZeroOrNullTotal30dVolume() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(any(), any())).thenReturn(new Metrics24h(0.0, 0L, false));
    when(volumeCache.getUniqueCounterparties24h(any())).thenReturn(0);

    when(cassandraHistoryRepository.countTransactionsInWindow(any(), any(), any())).thenReturn(0L);
    when(cassandraHistoryRepository.sumAmount(any(), any(), any())).thenReturn(null);
    when(cassandraHistoryRepository.sumP2pAmount(any(), any(), any())).thenReturn(null);

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
    assertThat(context.isDormantAccount()).isTrue(); // txCount30d == 0
  }

  @Test
  @DisplayName("Should calculate p2pRatio as 0.0 when p2pAmount is null but totalAmount is present")
  void shouldHandleNullP2pAmount() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(any(), any())).thenReturn(new Metrics24h(100.0, 1L, true));

    when(cassandraHistoryRepository.sumAmount(any(), any(), any()))
        .thenReturn(new BigDecimal("500.00"));
    when(cassandraHistoryRepository.sumP2pAmount(any(), any(), any())).thenReturn(null);

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
  }

  private Transaction createTransaction(String accountFrom, String accountTo, Instant createdAt) {
    return new Transaction(
        UUID.randomUUID(),
        accountFrom,
        accountTo,
        new Money(new BigDecimal("100.00"), "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        createdAt,
        createdAt);
  }
}
