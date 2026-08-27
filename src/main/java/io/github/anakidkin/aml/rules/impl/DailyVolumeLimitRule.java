package io.github.anakidkin.aml.rules.impl;


import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.rules.AmlRule;
import lombok.extern.slf4j.Slf4j;

/**
 * AML rule that validates transaction volume against maximum allowed daily cumulative limits.
 * Triggers a flag if the combined sum of previous 24-hour transfers and the current transaction
 * exceeds the permitted threshold, mitigating rapid capital flight or large-scale structuring.
 */
@Slf4j
public class DailyVolumeLimitRule implements AmlRule {

  private static final double DAILY_VOLUME_LIMIT = 100_000.00;

  @Override
  public String getRuleCode() {
    return "HARD_DAILY_VOLUME_EXCEEDED";
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
   * Evaluates whether the account's total 24-hour transfer volume, including the candidate
   * transaction, exceeds daily operational thresholds.
   *
   * @param transaction current transaction under evaluation
   * @param context     historical account metrics including 24-hour volume
   * @return {@link RuleResult} containing the evaluation status and execution details
   */
  @Override
  public RuleResult evaluate(Transaction transaction, AccountContext context) {
    long startTime = System.nanoTime();

    double totalVolume = context.volume24h() + transaction.money().amount().doubleValue();
    log.debug("{} totalVolume = {}", transaction.accountFrom(), totalVolume);
    boolean isFlagged = totalVolume > DAILY_VOLUME_LIMIT;
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;

    return new RuleResult(
        getRuleCode(),
        getRuleVersion(),
        isFlagged ? RuleStatus.FLAGGED : RuleStatus.PASSED,
        isFlagged ? String.format("24h total volume (%.2f) exceeds limit (%.2f)",
            totalVolume, DAILY_VOLUME_LIMIT) : null,
        durationMs,
        true
    );
  }
}