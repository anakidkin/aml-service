package io.github.anakidkin.aml.rules.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CounterpartyCountRuleTest {

  private CounterpartyCountRule rule;

  @BeforeEach
  void setUp() {
    rule = new CounterpartyCountRule();
  }

  @Test
  @DisplayName("Should return correct rule metadata")
  void shouldReturnCorrectMetadata() {
    assertThat(rule.getRuleCode()).isEqualTo("HARD_COUNTERPARTY_COUNT_EXCEEDED");
    assertThat(rule.getRuleVersion()).isZero();
    assertThat(rule.getPriority()).isEqualTo(1);
  }

  @ParameterizedTest(name = "Counterparties count={0} -> Status={1}, IsHard=true")
  @CsvSource({"0, PASSED", "5, PASSED", "10, PASSED", "11, FLAGGED", "100, FLAGGED"})
  @DisplayName(
      "Should flag transaction when 24h unique counterparties count exceeds threshold of 10")
  void shouldEvaluateCounterpartiesThreshold(int uniqueCounterparties, RuleStatus expectedStatus) {
    Transaction tx = createDummyTransaction();
    AccountContext context = new AccountContext(0.0, 0, uniqueCounterparties, 0.0, false);

    RuleResult result = rule.evaluate(tx, context);

    assertThat(result.ruleId()).isEqualTo("HARD_COUNTERPARTY_COUNT_EXCEEDED");
    assertThat(result.status()).isEqualTo(expectedStatus);
    assertThat(result.isHard()).isTrue();

    if (expectedStatus == RuleStatus.FLAGGED) {
      assertThat(result.triggerReason())
          .contains(
              "Unique counterparties in 24h (" + uniqueCounterparties + ") exceeds limit (10)");
    } else {
      assertThat(result.triggerReason()).isNull();
    }
  }

  private Transaction createDummyTransaction() {
    return new Transaction(
        UUID.randomUUID(),
        "ACC_1",
        "ACC_2",
        new Money(new BigDecimal("100.00"), "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now());
  }
}
