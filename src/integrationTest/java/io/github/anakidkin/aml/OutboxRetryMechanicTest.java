package io.github.anakidkin.aml;

import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.OutboxStatus;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class OutboxRetryMechanicTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("Should keep outbox status as PENDING when Kafka publish fails, and publish successfully on retry")
  void shouldKeepPendingOnKafkaFailureAndPublishOnRetry() {
    // Arrange
    UUID txId = UUID.randomUUID();
    Transaction tx = new Transaction(
        txId,
        "ACC_RETRY_1",
        "ACC_TO_2",
        new Money(new BigDecimal("100.00"), "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );

    // emulate kafka error
    doThrow(new RuntimeException("Kafka temporary unavailable"))
        .when(kafkaTemplateSpy).send(any(), any(), any());

    transactionEvaluationService.evaluate(tx);

    outboxEventRelay.publishPendingEvents();

    var outboxEvent = jpaOutboxRepository.findAll().stream()
        .filter(e -> e.getAggregateId().equals(txId))
        .findFirst()
        .orElseThrow();

    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(outboxEvent.getRetryCount()).isEqualTo(1);

    // fix kafka
    Mockito.reset(kafkaTemplateSpy);

    outboxEventRelay.publishPendingEvents();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var updatedOutbox = jpaOutboxRepository.findById(outboxEvent.getId()).orElseThrow();
          assertThat(updatedOutbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        });
  }
}