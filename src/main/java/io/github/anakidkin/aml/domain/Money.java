package io.github.anakidkin.aml.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
  public Money {
    Objects.requireNonNull(amount, "Amount cannot be null");
    Objects.requireNonNull(currency, "Currency cannot be null");

    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Amount cannot be negative");
    }
    if (currency.length() != 3) {
      throw new IllegalArgumentException("Currency code must be ISO 4217 (3 letters)");
    }
  }
}