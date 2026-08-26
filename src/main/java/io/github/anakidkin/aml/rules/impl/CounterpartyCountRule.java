package io.github.anakidkin.aml.rules.impl;


import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.rules.AmlRule;

/**
 * AML rule that evaluates the risk of suspicious fan-out or multi-account dispersion activity.
 * Triggers a flag if an account interacts with an unusually high number of unique counterparties
 * within a short time window (e.g., potential drop-card network activity).
 */
public class CounterpartyCountRule implements AmlRule {

  private static final int MAX_COUNTERPARTIES_24H = 10;

  @Override
  public String getRuleCode() {
    return "HARD_COUNTERPARTY_COUNT_EXCEEDED";
  }

  @Override
  public int getRuleVersion() {
    return 0;
  }

  @Override
  public int getPriority() {
    return 1;
  }

  /**
   * Evaluates whether the number of unique counterparties in the account context exceeds the predefined threshold.
   *
   * @param transaction current transaction under evaluation
   * @param context     historical account metrics including unique counterparty count
   * @return {@link RuleResult} containing the evaluation status and execution details
   */
  @Override
  public RuleResult evaluate(Transaction transaction, AccountContext context) {
    long startTime = System.nanoTime();

    boolean isFlagged = context.uniqueCounterparties24h() > MAX_COUNTERPARTIES_24H;
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;

    return new RuleResult(
        getRuleCode(),
        getRuleVersion(),
        isFlagged ? RuleStatus.FLAGGED : RuleStatus.PASSED,
        isFlagged ? String.format("Unique counterparties in 24h (%d) exceeds limit (%d)",
            context.uniqueCounterparties24h(), MAX_COUNTERPARTIES_24H) : null,
        durationMs,
        true
    );
  }
}