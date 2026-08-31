package io.github.anakidkin.aml.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Inbound Data Transfer Object representing an external request to evaluate a financial
 * transaction.
 *
 * @param accountFrom unique identifier of the originating account
 * @param accountTo unique identifier of the beneficiary account
 * @param amount monetary value of the transaction
 * @param currency three-letter ISO 4217 currency code
 * @param mccCode Four-digit Merchant Category Code classification (e.g., "6012" for financial
 *     institutions, "7995" for gambling).
 * @param isP2p Flag indicating whether the transaction is a Person-to-Person (P2P) transfer.
 *     Defaults to false if omitted.
 */
public record TransactionRequest(
    @NotBlank(message = "accountFrom is required") String accountFrom,
    @NotBlank(message = "accountTo is required") String accountTo,
    @NotNull(message = "amount is required") @Positive(message = "amount must be greater than 0")
        BigDecimal amount,
    @NotBlank(message = "currency is required") String currency,
    @Pattern(regexp = "^\\d{4}$", message = "mccCode must be a 4-digit numeric string")
        String mccCode,
    @NotNull(message = "isP2p flag must be provided") Boolean isP2p) {
  public TransactionRequest {
    Objects.requireNonNull(accountFrom, "accountFrom is required");
    Objects.requireNonNull(accountTo, "accountTo is required");
    Objects.requireNonNull(amount, "amount is required");
    Objects.requireNonNull(currency, "currency is required");
  }
}
