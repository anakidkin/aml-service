package io.github.anakidkin.aml.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Table(value = "account_counterparties", keyspace = "aml_ks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountCounterpartyEntity {

  @PrimaryKeyColumn(name = "account_to", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
  private String accountTo;

  @PrimaryKeyColumn(name = "time_window", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
  private String timeWindow; // YYYY-MM-DD

  @PrimaryKeyColumn(name = "account_from", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
  private String accountFrom;

  @Column("created_at")
  private Instant createdAt;
}