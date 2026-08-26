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

class DormantAccountSpikeRuleTest {

  private DormantAccountSpikeRule rule;

  @BeforeEach
  void setUp() {
    rule = new DormantAccountSpikeRule();
  }

  @Test
  @DisplayName("Should return correct rule metadata")
  void shouldReturnCorrectMetadata() {
    assertThat(rule.getRuleCode()).isEqualTo("BEHAVIOR_DORMANT_ACCOUNT_SPIKE");
    assertThat(rule.getRuleVersion()).isZero();
    assertThat(rule.getPriority()).isEqualTo(2);
  }

  @ParameterizedTest(name = "IsDormant={0}, Amount={1} -> Status={2}")
  @CsvSource({
      "false, 100000.00, PASSED",
      "true,  49999.99,  PASSED",
      "true,  50000.00,  FLAGGED",
      "true,  100000.00, FLAGGED"
  })
  @DisplayName("Should flag transaction on dormant account when amount is 50,000.00 or higher")
  void shouldEvaluateDormantSpike(boolean isDormant, String amount, RuleStatus expectedStatus) {
    Transaction tx = createTransaction(new BigDecimal(amount));
    AccountContext context = new AccountContext(
        0.0,
        0,
        0,
        0.0,
        isDormant
    );

    RuleResult result = rule.evaluate(tx, context);

    assertThat(result.ruleId()).isEqualTo("BEHAVIOR_DORMANT_ACCOUNT_SPIKE");
    assertThat(result.status()).isEqualTo(expectedStatus);
    assertThat(result.isHard()).isFalse();

    if (expectedStatus == RuleStatus.FLAGGED) {
      assertThat(result.triggerReason())
          .contains("Spike transaction")
          .contains("on dormant account");
    } else {
      assertThat(result.triggerReason()).isNull();
    }
  }

  private Transaction createTransaction(BigDecimal amount) {
    return new Transaction(
        UUID.randomUUID(),
        "ACC_1",
        "ACC_2",
        new Money(amount, "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );
  }
}