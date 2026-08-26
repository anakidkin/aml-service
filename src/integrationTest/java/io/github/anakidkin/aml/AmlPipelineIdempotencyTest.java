package io.github.anakidkin.aml;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AmlPipelineIdempotencyTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("Should process duplicate Kafka events idempotently without duplicating amounts in Cassandra")
  void shouldHandleDuplicateKafkaMessagesIdempotently() throws JsonProcessingException {
    // Arrange
    UUID txId = UUID.randomUUID();
    String accountFrom = "ACC_DUPLICATE_" + UUID.randomUUID().toString().substring(0, 8);
    BigDecimal amount = new BigDecimal("15000.00");
    Instant now = Instant.now();

    List<RuleResult> ruleResults = List.of(
        new RuleResult("RULE_MAX_AMOUNT", 1, RuleStatus.PASSED, "Amount is within limit", 12L, false),
        new RuleResult("RULE_MCC_CHECK", 1, RuleStatus.PASSED, "MCC code allowed", 5L, false)
    );

    TransactionEvaluatedEvent event = new TransactionEvaluatedEvent(
        txId,
        accountFrom,
        "ACC_TO_777",
        amount,
        "USD",
        "7995",
        false,
        TransactionStatus.APPROVED.name(),
        ruleResults,
        now,
        now
    );
    String payloadJson = objectMapper.writeValueAsString(event);

    // Act: send the same message twice
    kafkaTemplate.send(topicName, txId.toString(), payloadJson);
    kafkaTemplate.send(topicName, txId.toString(), payloadJson);

    // Assert: check that it processed just once
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(() -> {
          var auditRows = cassandraAuditRepository.findByTransactionId(txId);
          assertThat(auditRows).hasSize(2);

          BigDecimal actualSum = cassandraHistoryRepository.sumAmount(
              accountFrom,
              now.minus(Duration.ofMinutes(1)),
              now.plus(Duration.ofMinutes(1))
          );

          assertThat(actualSum)
              .isNotNull()
              .isEqualByComparingTo(amount);
        });
  }
}