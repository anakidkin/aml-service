package io.github.anakidkin.aml.domain;

/**
 * Represents the lifecycle stages and processing states of a transaction within the AML pipeline.
 */
public enum TransactionStatus {

  /**
   * Initial state when a transaction request is registered in the system prior to risk assessment.
   */
  NEW,

  /**
   * Indicates that the transaction is currently undergoing automated AML rule evaluation or pending
   * manual review.
   */
  PENDING,

  /** The transaction successfully passed all AML checks and is cleared for downstream execution. */
  APPROVED,

  /**
   * The transaction triggered one or more AML risk rules and requires heightened scrutiny or manual
   * compliance intervention.
   */
  FLAGGED,

  /**
   * The transaction was explicitly blocked due to severe risk thresholds, blacklisting, or policy
   * violations.
   */
  REJECTED
}
