package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.cache.AccountVolumeCache;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.repository.CassandraAccountCounterpartyRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.service.AccountContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AccountContextServiceImpl implements AccountContextService {


  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private final CassandraHistoryRepository cassandraHistoryRepository;
  private final CassandraAccountCounterpartyRepository cassandraAccountCounterpartyRepository;
  private final AccountVolumeCache volumeCache;


  @Override
  public AccountContext buildAccountContext(Transaction transaction) {

    Instant txTime = transaction.createdAt();
    Instant last24Hours = txTime.minus(24, ChronoUnit.HOURS);
    Instant last30Days = txTime.minus(30, ChronoUnit.DAYS);

    double volume24h = volumeCache.get24hVolume(transaction.accountFrom(), txTime);

    long txCount24h = cassandraHistoryRepository.countTransactionsInWindow(
        transaction.accountFrom(), last24Hours, txTime
    );

    String dateWindow = DATE_FORMATTER.format(txTime);
    int uniqueCounterparties24h = cassandraAccountCounterpartyRepository.countUniqueCounterparties(
        transaction.accountTo(), dateWindow
    );
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


    long txCount30d = cassandraHistoryRepository.countTransactionsInWindow(
        transaction.accountFrom(), last30Days, txTime
    );

    return new AccountContext(
        volume24h,
        txCount24h,
        uniqueCounterparties24h,
        p2pRatio30d,
        txCount30d == 0
    );
  }
}
