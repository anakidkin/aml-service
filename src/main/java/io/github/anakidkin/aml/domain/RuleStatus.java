package io.github.anakidkin.aml.domain;

/**
 * Represents the evaluation status returned by an AML rule.
 */
public enum RuleStatus {
  /**
   * The transaction fully passed the rule without triggering any risk conditions.
   * No suspicious activity detected.
   */
  PASSED,
  /**
   * The transaction triggered a risk threshold (e.g., daily volume exceeded) and requires attention.
   * The transaction is allowed to proceed, but an alert/event is generated for manual compliance review.
   */
  FLAGGED,
  /**
   * The transaction represents a severe AML violation or critical threat.
   * The transaction must be immediately blocked/rejected without execution.
   */
  BLOCKED
}