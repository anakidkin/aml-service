package io.github.anakidkin.aml.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class AccountVolumeCacheTest {

  @Mock private RedissonClient redisson;

  @Mock private CassandraHistoryRepository cassandraHistoryRepository;

  @Mock private RScoredSortedSet<String> scoredSortedSet;

  @Mock private RHyperLogLog<String> hyperLogLog;

  @Mock private RBucket<String> bucket;

  private AccountVolumeCache volumeCache;

  @BeforeEach
  void setUp() {
    volumeCache = new AccountVolumeCache(redisson, cassandraHistoryRepository);
  }

  @Test
  @DisplayName("Should return 24h metrics from Redis when cache key exists")
  void shouldReturnMetricsFromRedisWhenKeyExists() {
    String accountFrom = "ACC_1001";
    Instant now = Instant.now();

    when(redisson.<String>getScoredSortedSet("daily_volume:" + accountFrom))
        .thenReturn(scoredSortedSet);
    when(scoredSortedSet.isExists()).thenReturn(true);
    when(scoredSortedSet.iterator()).thenReturn(List.of("uuid1:100.0", "uuid2:250.5").iterator());
    when(scoredSortedSet.size()).thenReturn(2);

    Metrics24h metrics = volumeCache.get24hMetrics(accountFrom, now);

    assertThat(metrics.volume24h()).isEqualTo(350.5);
    assertThat(metrics.txCount24h()).isEqualTo(2);
    assertThat(metrics.hasHistoricalActivity()).isTrue();

    verify(scoredSortedSet).removeRangeByScore(eq(0d), eq(true), anyDouble(), eq(false));
    verify(cassandraHistoryRepository, never()).sumAmount(any(), any(), any());
  }

  @Test
  @DisplayName("Should fallback to Cassandra and populate Redis when cache key does not exist")
  void shouldFallbackToCassandraWhenKeyDoesNotExist() {
    String accountFrom = "ACC_1001";
    Instant now = Instant.now();

    when(redisson.<String>getScoredSortedSet("daily_volume:" + accountFrom))
        .thenReturn(scoredSortedSet);
    when(redisson.<String>getBucket("account_active:" + accountFrom)).thenReturn(bucket);
    when(scoredSortedSet.isExists()).thenReturn(false);

    when(cassandraHistoryRepository.sumAmount(eq(accountFrom), any(Instant.class), eq(now)))
        .thenReturn(new BigDecimal("500.00"));
    when(cassandraHistoryRepository.countTransactionsInWindow(
            eq(accountFrom), any(Instant.class), eq(now)))
        .thenReturn(5L);

    Metrics24h metrics = volumeCache.get24hMetrics(accountFrom, now);

    assertThat(metrics.volume24h()).isEqualTo(500.0);
    assertThat(metrics.txCount24h()).isEqualTo(1);
    assertThat(metrics.hasHistoricalActivity()).isTrue();

    verify(scoredSortedSet).add(now.toEpochMilli(), "INITIAL_HISTORICAL_SUM:500.0");
    verify(scoredSortedSet).expire(Duration.ofDays(1));
    verify(bucket).set("1", Duration.ofDays(60));
  }

  @Test
  @DisplayName("Should return count of unique counterparties from HyperLogLog")
  void shouldReturnUniqueCounterpartiesFromHll() {
    String accountTo = "ACC_2002";
    when(redisson.<String>getHyperLogLog("unique_counterparties:" + accountTo))
        .thenReturn(hyperLogLog);
    when(hyperLogLog.count()).thenReturn(42L);

    int count = volumeCache.getUniqueCounterparties24h(accountTo);

    assertThat(count).isEqualTo(42);
  }

  @Test
  @DisplayName("Should add transaction to ZSET, HLL and update active bucket")
  void shouldAddTransactionToRedis() {
    String accountFrom = "ACC_1001";
    String accountTo = "ACC_2002";
    Instant now = Instant.now();

    when(redisson.<String>getScoredSortedSet("daily_volume:" + accountFrom))
        .thenReturn(scoredSortedSet);
    when(redisson.<String>getHyperLogLog("unique_counterparties:" + accountTo))
        .thenReturn(hyperLogLog);
    when(redisson.<String>getBucket("account_active:" + accountFrom)).thenReturn(bucket);

    volumeCache.addTransaction(accountFrom, accountTo, 150.0, now);

    verify(scoredSortedSet).add(eq((double) now.toEpochMilli()), anyString());
    verify(scoredSortedSet).expire(Duration.ofDays(1));
    verify(hyperLogLog).add(accountFrom);
    verify(hyperLogLog).expire(Duration.ofDays(1));
    verify(bucket).set("1", Duration.ofDays(60));
  }
}
