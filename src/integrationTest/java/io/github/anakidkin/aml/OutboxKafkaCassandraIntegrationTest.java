package io.github.anakidkin.aml;

import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
@SpringBootTest
@Testcontainers
class OutboxKafkaCassandraIntegrationTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("Should evaluate transaction, save outbox, relay via Kafka and project to Cassandra")
  void shouldProcessFullOutboxToCassandraPipeline() {
    UUID txId = UUID.randomUUID();
    String accountFrom = "ACC_FROM_" + UUID.randomUUID().toString().substring(0, 8);
    BigDecimal expectedAmount = new BigDecimal("50000.00");
    Instant testStartTime = Instant.now().minus(Duration.ofSeconds(1));

    Transaction tx = new Transaction(
        txId,
        accountFrom,
        "ACC_TO_999",
        new Money(expectedAmount, "USD"),
        "7995",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );

    transactionEvaluationService.evaluate(tx);

    var outboxEvent = jpaOutboxRepository.findAll().stream()
        .filter(e -> e.getAggregateId().equals(txId))
        .findFirst();
    assertThat(outboxEvent).isPresent();
    assertThat(outboxEvent.get().getAggregateType()).isEqualTo("transaction");

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(() -> {
          var auditRow = cqlSession.execute(
              "SELECT transaction_id FROM aml_audit.audit_logs WHERE transaction_id = ?",
              txId
          ).one();
          assertThat(auditRow).isNotNull();
          assertThat(auditRow.getUuid("transaction_id")).isEqualTo(txId);

          BigDecimal actualSum = cassandraHistoryRepository.sumAmount(
              accountFrom,
              testStartTime,
              Instant.now().plus(Duration.ofMinutes(1))
          );
          assertThat(actualSum)
              .isNotNull()
              .isEqualByComparingTo(expectedAmount);
        });
  }
}