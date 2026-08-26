package io.github.anakidkin.aml.dto;

import io.github.anakidkin.aml.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Data Transfer Object containing the final AML evaluation verdict, assigned risk score, and transaction details.
 *
 * @param id          unique identifier of the evaluated transaction
 * @param accountFrom unique identifier of the originating account
 * @param accountTo   unique identifier of the beneficiary account
 * @param amount      monetary value of the transaction
 * @param currency    three-letter ISO 4217 currency code
 * @param status      assigned operational status (e.g., APPROVED, FLAGGED, REJECTED)
 * @param riskLevel   categorized risk level (e.g., LOW, MEDIUM, HIGH, CRITICAL)
 * @param riskScore   calculated numerical risk score
 * @param createdAt   timestamp when the transaction evaluation was performed
 */
public record TransactionResponse(
    UUID id,
    String accountFrom,
    String accountTo,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String riskLevel,
    Double riskScore,
    Instant createdAt
) {
}