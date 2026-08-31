package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.rules.AmlRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceImplTest {

  @Mock
  private AmlRule rule1;

  @Mock
  private AmlRule rule2;

  private RuleEngineServiceImpl ruleEngineService;

  @BeforeEach
  void setUp() {
    when(rule1.getPriority()).thenReturn(1);
    when(rule2.getPriority()).thenReturn(2);

    ruleEngineService = new RuleEngineServiceImpl(List.of(rule2, rule1));
  }

  @Test
  @DisplayName("Should evaluate all rules in priority order and return results")
  void shouldEvaluateAllRulesInPriorityOrder() {
    Transaction tx = createTransaction();
    AccountContext context = new AccountContext(0, 0, 0, 0, false);

    RuleResult res1 = new RuleResult("RULE_1", 1, RuleStatus.PASSED, "OK", 0, false);
    RuleResult res2 = new RuleResult("RULE_2", 2, RuleStatus.FLAGGED, "Flagged", 5, false);

    when(rule1.evaluate(tx, context)).thenReturn(res1);
    when(rule2.evaluate(tx, context)).thenReturn(res2);

    List<RuleResult> results = ruleEngineService.evaluate(tx, context);

    assertThat(results).containsExactly(res1, res2);
  }

  private Transaction createTransaction() {
    return new Transaction(
        UUID.randomUUID(), "ACC_1", "ACC_2",
        new Money(new BigDecimal("100"), "USD"), "5411", false,
        TransactionStatus.PENDING, null, Instant.now(), Instant.now()
    );
  }
}