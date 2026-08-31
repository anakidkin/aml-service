package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.domain.Transaction;

/** Primary inbound port (use case) defining the entry point for evaluating AML risk. */
public interface TransactionEvaluationService {

  /**
   * Processes an incoming transaction through the AML engine, aggregating account history,
   * executing risk rules, and persisting the resulting verdict.
   *
   * @param transaction the raw incoming transaction to evaluate
   * @return the evaluated transaction populated with status, risk score, and rule details
   */
  Transaction evaluate(Transaction transaction);
}
