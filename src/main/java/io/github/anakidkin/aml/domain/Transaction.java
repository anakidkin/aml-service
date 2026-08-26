package io.github.anakidkin.aml.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing a financial transaction passing through the AML evaluation pipeline.
 *
 * @param id             unique identifier of the transaction
 * @param accountFrom    source account identifier
 * @param accountTo      destination account identifier
 * @param money          monetary amount and currency of the operation
 * @param mccCode        four-digit Merchant Category Code classification
 * @param isP2p          flag indicating whether the transaction is a Person-to-Person transfer
 * @param status         current status of the transaction (e.g., APPROVED, FLAGGED)
 * @param riskAssessment detailed AML scoring verdict and executed rule results
 * @param createdAt      timestamp when the transaction was initiated
 * @param updatedAt      timestamp when the transaction status was last updated
 */
public record Transaction(
    UUID id,
    String accountFrom,
    String accountTo,
    Money money,
    String mccCode,
    Boolean isP2p,
    TransactionStatus status,
    RiskAssessment riskAssessment,
    Instant createdAt,
    Instant updatedAt
) {
  public Transaction {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(accountFrom, "accountFrom cannot be null");
    Objects.requireNonNull(accountTo, "accountTo cannot be null");
    Objects.requireNonNull(money, "money cannot be null");
    Objects.requireNonNull(status, "status cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
  }
}

