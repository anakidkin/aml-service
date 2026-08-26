package io.github.anakidkin.aml.dto;

import io.github.anakidkin.aml.domain.RuleResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionEvaluatedEvent(
    UUID transactionId,
    String accountFrom,
    String accountTo,
    BigDecimal amount,
    String currency,
    String mccCode,
    boolean isP2p,
    String status,
    List<RuleResult> ruleResults,
    Instant createdAt,
    Instant evaluatedAt
) {
}