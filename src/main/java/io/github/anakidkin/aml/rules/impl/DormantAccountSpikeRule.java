package io.github.anakidkin.aml.rules.impl;


import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.rules.AmlRule;

/**
 * AML rule that detects sudden high-value transactional activity on previously inactive (dormant) accounts.
 * Triggers a flag if an account with no operational history over a long period suddenly initiates
 * a transaction exceeding baseline velocity or volume limits (indicative of account takeover or compromised credentials).
 */
public class DormantAccountSpikeRule implements AmlRule {

  private static final double SPIKE_THRESHOLD = 50_000.00;

  @Override
  public String getRuleCode() {
    return "BEHAVIOR_DORMANT_ACCOUNT_SPIKE";
  }

  @Override
  public int getRuleVersion() {
    return 0;
  }

  @Override
  public int getPriority() {
    return 2;
  }

  /**
   * Evaluates whether the account is flagged as dormant and if the current transaction
   * represents a suspicious sudden spike in financial activity.
   *
   * @param transaction current transaction under evaluation
   * @param context     historical account metrics including dormancy status
   * @return {@link RuleResult} containing the evaluation status and execution details
   */
  @Override
  public RuleResult evaluate(Transaction transaction, AccountContext context) {
    long startTime = System.nanoTime();

    boolean isSpike = context.isDormantAccount()
        && transaction.money().amount().doubleValue() >= SPIKE_THRESHOLD;
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;

    return new RuleResult(
        getRuleCode(),
        getRuleVersion(),
        isSpike ? RuleStatus.FLAGGED : RuleStatus.PASSED,
        isSpike ? String.format("Spike transaction (%.2f) on dormant account",
            transaction.money().amount().doubleValue()) : null,
        durationMs,
        false
    );
  }
}