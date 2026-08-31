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

class DailyVolumeLimitRuleTest {

  private DailyVolumeLimitRule rule;

  @BeforeEach
  void setUp() {
    rule = new DailyVolumeLimitRule();
  }

  @Test
  @DisplayName("Should return correct rule metadata")
  void shouldReturnCorrectMetadata() {
    assertThat(rule.getRuleCode()).isEqualTo("HARD_DAILY_VOLUME_EXCEEDED");
    assertThat(rule.getRuleVersion()).isZero();
    assertThat(rule.getPriority()).isEqualTo(1);
  }

  @ParameterizedTest(
      name = "Historical 24h volume={0}, Current tx amount={1} -> Total={2}, Status={3}")
  @CsvSource({
    "0.0, 50000.00, PASSED",
    "50000.00, 50000.00, PASSED",
    "99999.99, 0.01, PASSED",
    "50000.00, 50000.01, FLAGGED",
    "100000.00, 0.01, FLAGGED",
    "120000.00, 1000.00, FLAGGED"
  })
  @DisplayName("Should flag transaction when cumulative 24h volume exceeds 100,000.00 limit")
  void shouldEvaluateDailyVolumeThreshold(
      double historicalVolume, String currentTxAmount, RuleStatus expectedStatus) {
    Transaction tx = createTransaction(new BigDecimal(currentTxAmount));
    AccountContext context = new AccountContext(historicalVolume, 10, 2, 0.1, false);

    RuleResult result = rule.evaluate(tx, context);

    assertThat(result.ruleId()).isEqualTo("HARD_DAILY_VOLUME_EXCEEDED");
    assertThat(result.status()).isEqualTo(expectedStatus);
    assertThat(result.isHard()).isTrue();

    if (expectedStatus == RuleStatus.FLAGGED) {
      assertThat(result.triggerReason())
          .contains("24h total volume")
          .contains("exceeds limit (100000.00)");
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
        Instant.now());
  }
}
