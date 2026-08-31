package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.cache.Metrics24h;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.service.AccountContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AccountContextServiceImpl implements AccountContextService {


  private final CassandraHistoryRepository cassandraHistoryRepository;
  private final AccountVolumeCache volumeCache;


  @Override
  public AccountContext buildAccountContext(Transaction transaction) {

    Instant txTime = transaction.createdAt();
    Instant last30Days = txTime.minus(30, ChronoUnit.DAYS);

    Metrics24h metrics24h = volumeCache.get24hMetrics(transaction.accountFrom(), txTime);
    int uniqueCounterparties24h = volumeCache.getUniqueCounterparties24h(transaction.accountTo());

    // If account has > 0 transactions in 24h window, it's DEFINITELY NOT dormant!
    // Query Cassandra for 30d window ONLY if 24h window is completely empty.
    boolean isDormantAccount;
    if (metrics24h.txCount24h() > 0) {
      isDormantAccount = false;
    } else {
      long txCount30d = cassandraHistoryRepository.countTransactionsInWindow(
          transaction.accountFrom(), last30Days, txTime
      );
      isDormantAccount = (txCount30d == 0);
    }

    /*
     * ARCHITECTURE TRADE-OFF:
     * - 24h metrics (volume, txCount, counterparties) are stored in Valkey/Redis to guarantee
     *   read-after-write consistency for high-frequency transactions and prevent false positives.
     * - 30d metrics (p2pRatio30d) are queried from Cassandra asynchronously:
     *   1. Memory Efficiency: Storing 30 days of raw transactions in Redis ZSETs creates high memory overhead.
     *   2. Business Tolerance: A minor lag (1-2s) in a 30-day window causes negligible deviation
     *      in the P2P ratio percentage and is fully acceptable for AML rules.
     */
    BigDecimal total30dVal = cassandraHistoryRepository.sumAmount(
        transaction.accountFrom(), last30Days, txTime
    );
    BigDecimal p2p30dVal = cassandraHistoryRepository.sumP2pAmount(
        transaction.accountFrom(), last30Days, txTime
    );
    double p2pRatio30d = 0.0;
    if (total30dVal != null && total30dVal.compareTo(BigDecimal.ZERO) > 0) {
      double total30d = total30dVal.doubleValue();
      double p2p30d = (p2p30dVal != null) ? p2p30dVal.doubleValue() : 0.0;
      p2pRatio30d = p2p30d / total30d;
    }

    return new AccountContext(
        metrics24h.volume24h(),
        metrics24h.txCount24h(),
        uniqueCounterparties24h,
        p2pRatio30d,
        isDormantAccount
    );
  }
}
