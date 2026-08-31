package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.repository.CassandraAccountCounterpartyRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountContextServiceImplTest {

  @Mock
  private CassandraHistoryRepository cassandraHistoryRepository;

  @Mock
  private CassandraAccountCounterpartyRepository cassandraAccountCounterpartyRepository;

  @Mock
  private AccountVolumeCache volumeCache;

  @InjectMocks
  private AccountContextServiceImpl accountContextService;

  @Test
  @DisplayName("Should correctly calculate all metrics when history exists")
  void shouldBuildAccountContextCorrectly() {
    Instant txTime = Instant.parse("2026-03-01T10:00:00Z");
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    Instant expected24hAgo = txTime.minus(24, ChronoUnit.HOURS);
    Instant expected30dAgo = txTime.minus(30, ChronoUnit.DAYS);

    when(volumeCache.get24hVolume("ACC_FROM", txTime)).thenReturn(1500.0);
    when(cassandraHistoryRepository.countTransactionsInWindow("ACC_FROM", expected24hAgo, txTime)).thenReturn(5L);
    when(cassandraAccountCounterpartyRepository.countUniqueCounterparties("ACC_TO", "2026-03-01")).thenReturn(3);

    when(cassandraHistoryRepository.sumAmount("ACC_FROM", expected30dAgo, txTime)).thenReturn(new BigDecimal("1000.00"));
    when(cassandraHistoryRepository.sumP2pAmount("ACC_FROM", expected30dAgo, txTime)).thenReturn(new BigDecimal("400.00"));
    when(cassandraHistoryRepository.countTransactionsInWindow("ACC_FROM", expected30dAgo, txTime)).thenReturn(20L);

    AccountContext context = accountContextService.buildAccountContext(tx);

    assertThat(context.volume24h()).isEqualTo(1500.0);
    assertThat(context.txCount24h()).isEqualTo(5L);
    assertThat(context.uniqueCounterparties24h()).isEqualTo(3);
    assertThat(context.p2pRatio30d()).isEqualTo(0.4); // 400.00 / 1000.00
    assertThat(context.isDormantAccount()).isFalse();  // 20L != 0
  }

  @Test
  @DisplayName("Should handle zero or null total 30d volume and set p2pRatio to 0.0")
  void shouldHandleZeroOrNullTotal30dVolume() {
    Instant txTime = Instant.now();
    Transaction tx = createTransaction("ACC_FROM", "ACC_TO", txTime);

    when(volumeCache.get24hVolume(any(), any())).thenReturn(0.0);
    when(cassandraHistoryRepository.countTransactionsInWindow(any(), any(), any())).thenReturn(0L);
    when(cassandraAccountCounterpartyRepository.countUniqueCounterparties(any(), any())).thenReturn(0);

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

    when(cassandraHistoryRepository.sumAmount(any(), any(), any())).thenReturn(new BigDecimal("500.00"));
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
        createdAt
    );
  }
}