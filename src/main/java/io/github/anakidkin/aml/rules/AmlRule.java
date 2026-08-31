package io.github.anakidkin.aml.rules;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;

/** Domain interface representing an individual Anti-Money Laundering (AML) risk evaluation rule. */
public interface AmlRule {

  /**
   * Returns the unique string identifier for this rule (e.g., "DAILY_VOLUME_LIMIT").
   *
   * @return unique rule code
   */
  String getRuleCode();

  /** Numeric version of the rule logic implementation for audit trail. */
  int getRuleVersion();

  /**
   * Returns the execution priority of the rule. Rules with lower priority values are evaluated
   * first.
   *
   * @return priority order
   */
  int getPriority();

  /**
   * Evaluates a single transaction against historical and behavioral context.
   *
   * @param transaction the transaction being evaluated
   * @param context historical and behavioral metrics associated with the account
   * @return evaluation result detailing status, triggers, and execution metadata
   */
  RuleResult evaluate(Transaction transaction, AccountContext context);
}
