package io.github.anakidkin.aml.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.dto.InboundTransactionEvent;
import io.github.anakidkin.aml.mapper.TransactionDomainMapper;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboundTransactionListener {

  private final TransactionEvaluationService evaluationService;
  private final TransactionDomainMapper transactionDomainMapper;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "${aml.kafka.topics.inbound-transactions:aml.transactions-inbound.v1}",
      groupId = "${aml.kafka.groups.core-processor:aml-core-processor-group}"
  )
  public void onInboundTransaction(String payload) {
    try {
      InboundTransactionEvent event = objectMapper.readValue(payload, InboundTransactionEvent.class);
      log.info("Received inbound transaction event txId={}", event.transactionId());
      Transaction transaction = transactionDomainMapper.toDomain(event);
      evaluationService.evaluate(transaction);
    } catch (DataIntegrityViolationException _) {
      log.warn("Duplicate transaction received, ignoring processing. Payload: {}", payload);
    } catch (Exception e) {
      log.error("Failed to process inbound transaction payload: {}", payload, e);
      throw new RuntimeException("Inbound transaction processing error", e);
    }
  }
}