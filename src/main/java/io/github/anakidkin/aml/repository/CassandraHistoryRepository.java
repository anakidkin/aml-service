package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.entity.AccountTransactionHistoryEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CassandraHistoryRepository
    extends CassandraRepository<AccountTransactionHistoryEntity, Map<String, Object>> {

  @Query(
      "SELECT COUNT(*) "
          + "FROM aml_ks.account_transaction_history "
          + "WHERE account_from = ?0 AND is_p2p IN (true, false) AND created_at >= ?1 AND created_at <= ?2")
  long countTransactionsInWindow(String accountFrom, Instant from, Instant to);

  @Query(
      "SELECT SUM(amount) "
          + "FROM aml_ks.account_transaction_history "
          + "WHERE account_from = ?0 AND is_p2p IN (true, false) AND created_at >= ?1 AND created_at <= ?2")
  BigDecimal sumAmount(String accountFrom, Instant from, Instant to);

  @Query(
      "SELECT SUM(amount) "
          + "FROM aml_ks.account_transaction_history "
          + "WHERE account_from = ?0 AND is_p2p = true AND created_at >= ?1 AND created_at <= ?2")
  BigDecimal sumP2pAmount(String accountFrom, Instant from, Instant to);
}
