package io.github.anakidkin.aml.cache;

import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountVolumeCache {

  private static final String VOLUME_KEY_PREFIX = "daily_volume:";
  private static final String HLL_KEY_PREFIX = "unique_counterparties:";
  private static final String EVER_ACTIVE_PREFIX = "account_active:";

  private final RedissonClient redisson;
  private final CassandraHistoryRepository cassandraHistoryRepository;

  /**
   * Fetches 24-hour volume, transaction count, and activity status from Redis.
   * MUST be executed within Redisson Lock context!
   */
  public Metrics24h get24hMetrics(String accountFrom, Instant txTime) {
    String volumeKey = VOLUME_KEY_PREFIX + accountFrom;
    RScoredSortedSet<String> set = redisson.getScoredSortedSet(volumeKey);

    long windowStartMs = txTime.minus(24, ChronoUnit.HOURS).toEpochMilli();

    if (!set.isExists()) {
      log.debug("Volume key {} does not exist in Redis, populating from Cassandra", volumeKey);
      return fetchAndPopulateFromCassandra(accountFrom, txTime, set);
    }

    set.removeRangeByScore(0, true, windowStartMs, false);

    double sum = calculateSum(set);
    long count = set.size();

    return new Metrics24h(sum, count, true); // Active because key exists
  }

  /**
   * Returns estimated count of unique counterparties interacting with accountTo in 24h.
   */
  public int getUniqueCounterparties24h(String accountTo) {
    String hllKey = HLL_KEY_PREFIX + accountTo;
    RHyperLogLog<String> hll = redisson.getHyperLogLog(hllKey);
    return (int) hll.count();
  }

  /**
   * Adds an approved transaction to the Redis sliding window.
   */
  public void addTransaction(String accountFrom, String accountTo, double amount, Instant txTime) {
    log.debug("Adding amount {} to cache for account {}", amount, accountFrom);

    // 1. Update 24h Sliding Window ZSET
    String volumeKey = VOLUME_KEY_PREFIX + accountFrom;
    RScoredSortedSet<String> set = redisson.getScoredSortedSet(volumeKey);
    String member = UUID.randomUUID() + ":" + amount;
    set.add(txTime.toEpochMilli(), member);
    set.expire(Duration.ofDays(1));

    // 2. Update Unique Counterparties HLL (for accountTo)
    String hllKey = HLL_KEY_PREFIX + accountTo;
    RHyperLogLog<String> hll = redisson.getHyperLogLog(hllKey);
    hll.add(accountFrom);
    hll.expire(Duration.ofDays(1));

    // 3. Mark account as active forever/long-term (for fast dormant check)
    redisson.getBucket(EVER_ACTIVE_PREFIX + accountFrom).set("1", Duration.ofDays(60));
  }

  private Metrics24h fetchAndPopulateFromCassandra(
      String accountFrom, Instant txTime, RScoredSortedSet<String> set) {

    Instant last24Hours = txTime.minus(24, ChronoUnit.HOURS);
    Instant last30Days = txTime.minus(30, ChronoUnit.DAYS);
    BigDecimal volumeVal = cassandraHistoryRepository.sumAmount(accountFrom, last24Hours, txTime);
    long txCount30d = cassandraHistoryRepository.countTransactionsInWindow(accountFrom, last30Days, txTime);
    double volume24h = (volumeVal != null) ? volumeVal.doubleValue() : 0.0;

    if (volume24h > 0) {
      set.add(txTime.toEpochMilli(), "INITIAL_HISTORICAL_SUM:" + volume24h);
    }
    set.expire(Duration.ofDays(1));

    boolean hasActivity = txCount30d > 0;
    if (hasActivity) {
      redisson.getBucket(EVER_ACTIVE_PREFIX + accountFrom).set("1", Duration.ofDays(60));
    }

    return new Metrics24h(volume24h, volume24h > 0 ? 1 : 0, hasActivity);
  }

  private double calculateSum(RScoredSortedSet<String> set) {
    double sum = 0.0;
    for (String member : set) {
      String[] parts = member.split(":");
      if (parts.length == 2) {
        sum += Double.parseDouble(parts[1]);
      }
    }
    log.debug("Calculated sum: {}", sum);
    return sum;
  }
}