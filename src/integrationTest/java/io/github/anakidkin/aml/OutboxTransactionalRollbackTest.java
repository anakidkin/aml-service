package io.github.anakidkin.aml;

import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class OutboxTransactionalRollbackTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("Should rollback outbox record when main transaction processing fails")
  void shouldRollbackOutboxWhenTransactionFails() {
    // Arrange
    UUID txId = UUID.randomUUID();
    Transaction tx = new Transaction(
        txId,
        "ACC_FAIL_1",
        "ACC_TO_2",
        new Money(new BigDecimal("500.00"), "USD"),
        "5411",
        false,
        TransactionStatus.PENDING,
        null,
        Instant.now(),
        Instant.now()
    );

    // emulate DB error
    doThrow(new RuntimeException("Database error during transaction save"))
        .when(jpaTransactionRepository).save(any());

    assertThatThrownBy(() -> transactionEvaluationService.evaluate(tx))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Database error");

    var outboxEvents = jpaOutboxRepository.findAll().stream()
        .filter(e -> e.getAggregateId().equals(txId))
        .toList();

    assertThat(outboxEvents).isEmpty();
  }
}