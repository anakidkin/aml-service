package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.cache.Metrics24h;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.dto.AccountStatsProjection;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.service.AccountContextService;
import io.micrometer.core.annotation.Timed;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountContextServiceImpl implements AccountContextService {

  private final CassandraHistoryRepository cassandraHistoryRepository;
  private final AccountVolumeCache volumeCache;

  @Override
  @Timed(
      value = "aml.context.build.latency",
      description = "Transaction context building latency",
      percentiles = {0.50, 0.95, 0.99, 0.999})
  public AccountContext buildAccountContext(Transaction transaction) {
    /*
     * ARCHITECTURE TRADE-OFF:
     * - 24h metrics (volume, txCount, counterparties) are stored in Valkey/Redis to guarantee
     *   read-after-write consistency for high-frequency transactions and prevent false positives.
     * - 30d metrics (p2pRatio30d) are queried from Cassandra asynchronously:
     *   1. Memory Efficiency: Storing 30 days of raw transactions in Redis ZSETs creates high memory overhead.
     *   2. Business Tolerance: A minor lag (1-2s) in a 30-day window causes negligible deviation
     *      in the P2P ratio percentage and is fully acceptable for AML rules.
     */
    Instant txTime = transaction.createdAt();
    Instant last30Days = txTime.minus(30, ChronoUnit.DAYS);

    Metrics24h metrics24h = volumeCache.get24hMetrics(transaction.accountFrom(), txTime);
    int uniqueCounterparties24h = volumeCache.getUniqueCounterparties24h(transaction.accountTo());

    List<AccountStatsProjection> statsList =
        cassandraHistoryRepository.fetchGroupedStats(transaction.accountFrom(), last30Days, txTime);

    long totalCount30d = 0;
    BigDecimal total30dVal = BigDecimal.ZERO;
    BigDecimal p2p30dVal = BigDecimal.ZERO;

    for (AccountStatsProjection stat : statsList) {
      boolean isP2p = Boolean.TRUE.equals(stat.getIsP2p());
      long count = stat.getTxCount() != null ? stat.getTxCount() : 0L;
      BigDecimal sum = (stat.getTotalAmount() != null) ? stat.getTotalAmount() : BigDecimal.ZERO;

      totalCount30d += count;
      total30dVal = total30dVal.add(sum);

      if (isP2p) {
        p2p30dVal = sum;
      }
    }

    boolean isDormantAccount = (metrics24h.txCount24h() == 0) && (totalCount30d == 0);

    double p2pRatio30d = 0.0;
    if (total30dVal.compareTo(BigDecimal.ZERO) > 0) {
      p2pRatio30d = p2p30dVal.doubleValue() / total30dVal.doubleValue();
    }

    return new AccountContext(
        metrics24h.volume24h(),
        metrics24h.txCount24h(),
        uniqueCounterparties24h,
        p2pRatio30d,
        isDormantAccount);
  }
}
