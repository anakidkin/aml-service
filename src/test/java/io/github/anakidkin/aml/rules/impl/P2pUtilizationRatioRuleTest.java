package io.github.anakidkin.aml.rules.impl;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class P2pUtilizationRatioRuleTest {

  private P2pUtilizationRatioRule rule;

  @BeforeEach
  void setUp() {
    rule = new P2pUtilizationRatioRule();
  }

  @Test
  @DisplayName("Should return correct rule metadata")
  void shouldReturnCorrectMetadata() {
    assertThat(rule.getRuleCode()).isEqualTo("BEHAVIOR_HIGH_P2P_RATIO");
    assertThat(rule.getRuleVersion()).isZero();
    assertThat(rule.getPriority()).isEqualTo(2);
  }

  @ParameterizedTest(name = "P2P ratio 30d={0} -> Status={1}")
  @CsvSource({
      "0.00, PASSED",
      "0.50, PASSED",
      "0.80, PASSED",
      "0.801, FLAGGED",
      "0.95, FLAGGED",
      "1.00, FLAGGED"
  })
  @DisplayName("Should flag transaction when 30-day P2P ratio strictly exceeds 80%")
  void shouldEvaluateP2pRatioThreshold(double p2pRatio, RuleStatus expectedStatus) {
    Transaction tx = createTransaction();
    AccountContext context = new AccountContext(
        0.0,
        0,
        0,
        p2pRatio,
        false
    );

    RuleResult result = rule.evaluate(tx, context);

    assertThat(result.ruleId()).isEqualTo("BEHAVIOR_HIGH_P2P_RATIO");
    assertThat(result.status()).isEqualTo(expectedStatus);
    assertThat(result.isHard()).isFalse();

    if (expectedStatus == RuleStatus.FLAGGED) {
      assertThat(result.triggerReason())
          .contains("P2P ratio")
          .contains("exceeds threshold (80%)");
    } else {
      assertThat(result.triggerReason()).isNull();
    }
  }

  private Transaction createTransaction() {
    return new Transaction(
        UUID.randomUUID(),
        "ACC_1",
        "ACC_2",
        new Money(new BigDecimal("100.00"), "USD"),
        "5411",
        true,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );
  }
}