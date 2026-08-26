package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CassandraHistoryRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("Transactions outside the 1-hour window should not be included in sumAmount")
  void shouldIgnoreTransactionsOutsideTimeWindow() {
    Instant now = Instant.now();
    Instant windowStart = now.minus(Duration.ofHours(1));
    Instant windowEnd = now.plus(Duration.ofHours(1));
    var accountId = UUID.randomUUID().toString();

    insertTransactionToCassandra(accountId, new BigDecimal("500.00"), now.minus(Duration.ofHours(2)));
    insertTransactionToCassandra(accountId, new BigDecimal("150.00"), now.minus(Duration.ofMinutes(30)));

    var sum = cassandraHistoryRepository.sumAmount(accountId, windowStart, windowEnd);

    assertThat(sum).isEqualTo(new BigDecimal("150.00"));
  }

  @Test
  @DisplayName("Aggregation of multiple transactions (100 + 200 + 300 = 600)")
  void shouldCorrectlyAggregateMultipleTransactionsInPipeline() {
    Instant now = Instant.now();
    Instant windowStart = now.minus(Duration.ofHours(1));
    Instant windowEnd = now.plus(Duration.ofHours(2));
    var accountId = UUID.randomUUID().toString();

    insertTransactionToCassandra(accountId, new BigDecimal("100.00"), now);
    insertTransactionToCassandra(accountId, new BigDecimal("200.00"), now);
    insertTransactionToCassandra(accountId, new BigDecimal("300.00"), now);

    var totalSum = cassandraHistoryRepository.sumAmount(accountId, windowStart, windowEnd);

    assertThat(totalSum).isEqualTo(new BigDecimal("600.00"));
  }

  private void insertTransactionToCassandra(String accountFrom, BigDecimal amount, Instant timestamp) {
    String cql = """
        INSERT INTO aml_ks.account_transaction_history
        (account_from, is_p2p, created_at, transaction_id, account_to, amount, currency, mcc_code)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    cqlSession.execute(
        cql,
        accountFrom,
        false,
        timestamp,
        UUID.randomUUID(),
        "ACC_COUNTERPARTY",
        amount,
        "USD",
        "5411"
    );
  }
}