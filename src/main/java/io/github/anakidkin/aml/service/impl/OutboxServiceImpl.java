package io.github.anakidkin.aml.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import io.github.anakidkin.aml.entity.TransactionEntity;
import io.github.anakidkin.aml.mapper.TransactionDomainMapper;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import io.github.anakidkin.aml.service.OutboxService;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

  private final JpaTransactionRepository jpaTransactionRepository;
  private final JpaOutboxRepository jpaOutboxRepository;
  private final TransactionDomainMapper transactionDomainMapper;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  @Timed(
      value = "aml.outbox.save.latency",
      description = "Outbox saving latency",
      percentiles = {0.50, 0.95, 0.99, 0.999})
  public Transaction save(Transaction transaction, List<RuleResult> ruleResults) {
    TransactionEntity entity = transactionDomainMapper.toEntity(transaction);
    jpaTransactionRepository.save(entity);
    saveOutboxEvent(transaction, ruleResults);
    return transaction;
  }

  private void saveOutboxEvent(Transaction tx, List<RuleResult> ruleResults) {
    var event = transactionDomainMapper.toEvaluatedEvent(tx, ruleResults);
    try {
      String payloadJson = objectMapper.writeValueAsString(event);

      OutboxEventEntity outboxEvent =
          OutboxEventEntity.builder()
              .aggregateId(tx.id())
              .aggregateType("transaction")
              .eventType("TRANSACTION_EVALUATED")
              .payload(payloadJson)
              .build();

      jpaOutboxRepository.save(outboxEvent);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize transaction payload for outbox", e);
    }
  }
}
