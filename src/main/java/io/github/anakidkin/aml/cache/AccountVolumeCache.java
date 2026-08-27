package io.github.anakidkin.aml.cache;

import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private static final String CACHE_KEY_PREFIX = "daily_volume:";
  private final RedissonClient redisson;
  private final CassandraHistoryRepository cassandraHistoryRepository;

  /**
   * Returns the exact 24-hour volume considering a rolling time window.
   * MUST be executed within a Redisson Lock context!
   */
  public double get24hVolume(String accountFrom, Instant txTime) {
    String key = CACHE_KEY_PREFIX + accountFrom;
    RScoredSortedSet<String> set = redisson.getScoredSortedSet(key);

    long windowStartMs = txTime.minus(24, ChronoUnit.HOURS).toEpochMilli();

    if (!set.isExists()) {
      log.debug("Key {} does not exist", key);
      return fetchAndPopulateFromCassandra(accountFrom, txTime, set);
    }

    set.removeRangeByScore(0, true, windowStartMs, false);

    return calculateSum(set);
  }

  /**
   * Adds an approved transaction to the Redis sliding window.
   */
  public void addAmount(String accountFrom, double amount, Instant txTime) {
    log.debug("Adding amount {} to cache for account {}", amount, accountFrom);
    String key = CACHE_KEY_PREFIX + accountFrom;
    RScoredSortedSet<String> set = redisson.getScoredSortedSet(key);

    // Store a unique "id:amount" pair as the set member
    String member = UUID.randomUUID() + ":" + amount;
    set.add(txTime.toEpochMilli(), member);

    // Extend TTL for another 24 hours from the latest activity
    set.expire(Duration.ofDays(1));
  }

  private double fetchAndPopulateFromCassandra(
      String accountFrom, Instant txTime, RScoredSortedSet<String> set) {

    Instant last24Hours = txTime.minus(24, ChronoUnit.HOURS);
    BigDecimal volumeVal = cassandraHistoryRepository.sumAmount(accountFrom, last24Hours, txTime);
    double volume24h = (volumeVal != null) ? volumeVal.doubleValue() : 0.0;

    if (volume24h > 0) {
      set.add(txTime.toEpochMilli(), "INITIAL_HISTORICAL_SUM:" + volume24h);
    }
    set.expire(Duration.ofDays(1));

    return volume24h;
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