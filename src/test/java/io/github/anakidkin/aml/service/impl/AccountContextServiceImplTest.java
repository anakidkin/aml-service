package io.github.anakidkin.aml.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.cache.Metrics24h;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.dto.AccountStatsProjection;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
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
      "Should correctly calculate all metrics and p2pRatio from single Cassandra grouped query")
  void shouldBuildAccountContextCorrectly() {
    Instant txTime = Instant.parse("2026-03-01T10:00:00Z");
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    Instant expected30dAgo = txTime.minus(30, ChronoUnit.DAYS);

    // Valkey/Redis metrics for 24h
    when(volumeCache.get24hMetrics("ACC_FROM", txTime))
        .thenReturn(new Metrics24h(1500.0, 5L, true));
    when(volumeCache.getUniqueCounterparties24h("ACC_TO")).thenReturn(3);

    // Cassandra metrics for 30d
    AccountStatsProjection p2pStat = createStatProjection(true, 2L, new BigDecimal("400.00"));
    AccountStatsProjection nonP2pStat = createStatProjection(false, 3L, new BigDecimal("600.00"));
    when(cassandraHistoryRepository.fetchGroupedStats("ACC_FROM", expected30dAgo, txTime))
        .thenReturn(List.of(p2pStat, nonP2pStat));

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.volume24h()).isEqualTo(1500.0);
    assertThat(context.txCount24h()).isEqualTo(5L);
    assertThat(context.uniqueCounterparties24h()).isEqualTo(3);
    assertThat(context.p2pRatio30d()).isEqualTo(0.4); // 400.00 / 1000.00
    assertThat(context.isDormantAccount()).isFalse(); // txCount24h > 0 -> guarantees active account

    verify(cassandraHistoryRepository).fetchGroupedStats("ACC_FROM", expected30dAgo, txTime);
    verify(cassandraHistoryRepository, never()).countTransactionsInWindow(any(), any(), any());
    verify(cassandraHistoryRepository, never()).sumAmount(any(), any(), any());
  }

  @Test
  @DisplayName(
      "Should mark account as dormant when inactive in both 24h cache and 30d Cassandra history")
  void shouldQueryCassandraWhenInactiveIn24h() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(eq("ACC_FROM"), any(Instant.class)))
        .thenReturn(new Metrics24h(0.0, 0L, false));
    when(volumeCache.getUniqueCounterparties24h("ACC_TO")).thenReturn(0);

    when(cassandraHistoryRepository.fetchGroupedStats(eq("ACC_FROM"), any(), any()))
        .thenReturn(Collections.emptyList());

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.volume24h()).isEqualTo(0.0);
    assertThat(context.txCount24h()).isZero();
    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
    assertThat(context.isDormantAccount()).isTrue();
  }

  @Test
  @DisplayName("Should handle zero or null total 30d volume and set p2pRatio to 0.0")
  void shouldHandleZeroOrNullTotal30dVolume() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(any(), any())).thenReturn(new Metrics24h(0.0, 0L, false));
    when(volumeCache.getUniqueCounterparties24h(any())).thenReturn(0);

    when(cassandraHistoryRepository.fetchGroupedStats(any(), any(), any()))
        .thenReturn(Collections.emptyList());

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
    assertThat(context.isDormantAccount()).isTrue(); // txCount30d == 0
  }

  @Test
  @DisplayName("Should calculate p2pRatio as 0.0 when no P2P transactions exist in 30d stats")
  void shouldHandleNullP2pAmount() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hMetrics(any(), any())).thenReturn(new Metrics24h(100.0, 1L, true));

    AccountStatsProjection nonP2pStat = createStatProjection(false, 1L, new BigDecimal("500.00"));
    when(cassandraHistoryRepository.fetchGroupedStats(any(), any(), any()))
        .thenReturn(List.of(nonP2pStat));

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.p2pRatio30d()).isEqualTo(0.0);
  }

  private AccountStatsProjection createStatProjection(
      Boolean isP2p, Long txCount, BigDecimal totalAmount) {
    AccountStatsProjection mock = mock(AccountStatsProjection.class);
    when(mock.getIsP2p()).thenReturn(isP2p);
    when(mock.getTxCount()).thenReturn(txCount);
    when(mock.getTotalAmount()).thenReturn(totalAmount);
    return mock;
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
