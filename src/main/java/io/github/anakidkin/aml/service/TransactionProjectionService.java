package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;

/**
 * Service-projection responsible for processing and storing transaction history and audit logs from
 * outbox event streams.
 */
public interface TransactionProjectionService {

  /**
   * Processes a transaction evaluation event and persists denormalized data into the history store.
   *
   * @param event the transaction evaluation event payload
   */
  void projectTransactionEvaluation(TransactionEvaluatedEvent event);
}
