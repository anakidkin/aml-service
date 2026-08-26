package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.entity.AuditLogEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CassandraAuditRepository extends CassandraRepository<AuditLogEntity, Map<String, Object>> {
  List<AuditLogEntity> findByTransactionId(UUID transactionId);
}