package io.github.anakidkin.aml.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;
import io.github.anakidkin.aml.service.TransactionProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEvaluatedHistoryListener {

  private final ObjectMapper objectMapper;
  private final TransactionProjectionService transactionProjectionService;

  @KafkaListener(
      topics = "${aml.kafka.topics.transaction-evaluated:aml.transaction-evaluated.v1}",
      groupId = "${aml.kafka.groups.history-writer:aml-history-writer-group}"
  )
  public void onTransactionEvaluated(String payload) {
    log.info("onTransactionEvaluated payload = {}", payload);
    try {
      TransactionEvaluatedEvent event = objectMapper.readValue(payload, TransactionEvaluatedEvent.class);
      transactionProjectionService.projectTransactionEvaluation(event);
      log.debug("Successfully saved transaction history to Cassandra for tx={}", event.transactionId());
    } catch (Exception e) {
      log.error("Failed to process transaction history event for payload: {}", payload, e);
      throw new RuntimeException("Error persisting event to Cassandra", e);
    }
  }

}