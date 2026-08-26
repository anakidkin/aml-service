package io.github.anakidkin.aml.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InboundTransactionEvent(
    UUID transactionId,
    String accountFrom,
    String accountTo,
    BigDecimal amount,
    String currency,
    String mccCode,
    boolean isP2p,
    Instant timestamp
) {
}