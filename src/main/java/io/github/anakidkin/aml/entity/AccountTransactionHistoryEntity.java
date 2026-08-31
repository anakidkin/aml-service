package io.github.anakidkin.aml.entity;

import java.math.BigDecimal;
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

@Table(value = "account_transaction_history", keyspace = "aml_ks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransactionHistoryEntity {

  @PrimaryKeyColumn(name = "account_from", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
  private String accountFrom;

  @PrimaryKeyColumn(
      name = "is_p2p",
      type = PrimaryKeyType.CLUSTERED,
      ordering = Ordering.ASCENDING,
      ordinal = 1)
  private boolean isP2p;

  @PrimaryKeyColumn(
      name = "created_at",
      type = PrimaryKeyType.CLUSTERED,
      ordering = Ordering.DESCENDING,
      ordinal = 2)
  private Instant createdAt;

  @PrimaryKeyColumn(
      name = "transaction_id",
      type = PrimaryKeyType.CLUSTERED,
      ordering = Ordering.ASCENDING,
      ordinal = 3)
  private UUID transactionId;

  @Column("account_to")
  private String accountTo;

  @Column("amount")
  private BigDecimal amount;

  @Column("currency")
  private String currency;

  @Column("mcc_code")
  private String mccCode;
}
