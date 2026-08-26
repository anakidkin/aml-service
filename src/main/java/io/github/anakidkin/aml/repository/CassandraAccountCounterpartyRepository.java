package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.entity.AccountCounterpartyEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public interface CassandraAccountCounterpartyRepository
    extends CassandraRepository<AccountCounterpartyEntity, Map<String, Object>> {

  @Query("""
          SELECT COUNT(*) FROM aml_ks.account_counterparties
          WHERE account_to = ?0 AND time_window = ?1
      """)
  int countUniqueCounterparties(String accountTo, String timeWindow);
}