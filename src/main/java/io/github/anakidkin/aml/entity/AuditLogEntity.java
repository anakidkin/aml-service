package io.github.anakidkin.aml.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@Table(value = "audit_logs", keyspace = "aml_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

  @PrimaryKeyColumn(name = "transaction_id", type = PrimaryKeyType.PARTITIONED)
  private UUID transactionId;

  @PrimaryKeyColumn(
      name = "created_at",
      type = PrimaryKeyType.CLUSTERED,
      ordering = Ordering.DESCENDING)
  private Instant createdAt;

  @PrimaryKeyColumn(
      name = "rule_id",
      ordinal = 1,
      type = PrimaryKeyType.CLUSTERED,
      ordering = Ordering.ASCENDING)
  private String ruleId;

  @Column("rule_version")
  private int ruleVersion;

  @Column("status")
  private String status;

  @Column("trigger_reason")
  private String triggerReason;

  @Column("execution_time_ms")
  private long executionTimeMs;

  @Column("is_hard")
  private boolean isHard;

  @Column("risk_score")
  private Double riskScore;
}
