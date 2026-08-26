package io.github.anakidkin.aml.rules.impl;


import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.rules.AmlRule;

/**
 * AML rule that evaluates the proportion of peer-to-peer (P2P) transfers relative to overall account activity.
 * Triggers a flag if the 30-day P2P volume ratio exceeds permitted risk thresholds, targeting potential transit
 * account usage, unlicenced money remitting, or unauthorized transit corridors.
 */
public class P2pUtilizationRatioRule implements AmlRule {

  private static final double MAX_P2P_RATIO = 0.80; // 80%

  @Override
  public String getRuleCode() {
    return "BEHAVIOR_HIGH_P2P_RATIO";
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
   * Evaluates whether the account's historical 30-day P2P utilization ratio exceeds acceptable limits.
   *
   * @param transaction current transaction under evaluation
   * @param context     historical account metrics including 30-day P2P ratio
   * @return {@link RuleResult} containing the evaluation status and execution details
   */
  @Override
  public RuleResult evaluate(Transaction transaction, AccountContext context) {
    long startTime = System.nanoTime();

    boolean isHighP2p = context.p2pRatio30d() > MAX_P2P_RATIO;
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;

    return new RuleResult(
        getRuleCode(),
        getRuleVersion(),
        isHighP2p ? RuleStatus.FLAGGED : RuleStatus.PASSED,
        isHighP2p ? String.format("P2P ratio (%.2f%%) exceeds threshold (80%%)",
            context.p2pRatio30d() * 100) : null,
        durationMs,
        false
    );
  }
}