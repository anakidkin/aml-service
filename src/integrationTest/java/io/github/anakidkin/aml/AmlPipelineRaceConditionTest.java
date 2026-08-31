package io.github.anakidkin.aml;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmlPipelineRaceConditionTest extends AbstractIntegrationTest {

  @Test
  @DisplayName(
      "Should expose race condition: simultaneous service invocations bypass daily volume limit due to async history lag")
  void shouldExposeDailyLimitRaceConditionOnConcurrentServiceCalls() {
    // Arrange
    String accountFrom = "ACC_RACE_" + UUID.randomUUID().toString().substring(0, 8);
    String accountTo = "ACC_DEST_" + UUID.randomUUID().toString().substring(0, 8);
    BigDecimal chunkAmount =
        new BigDecimal("60000.00"); // 2 x 60k = 120k (> 100k limit, see DailyVolumeLimitRule)
    Instant now = Instant.now();

    Transaction tx1 =
        new Transaction(
            UUID.randomUUID(),
            accountFrom,
            accountTo,
            new Money(chunkAmount, "EUR"),
            "5999",
            false,
            TransactionStatus.NEW,
            null,
            now,
            now);
    Transaction tx2 =
        new Transaction(
            UUID.randomUUID(),
            accountFrom,
            accountTo,
            new Money(chunkAmount, "EUR"),
            "5999",
            false,
            TransactionStatus.NEW,
            null,
            now.plusMillis(10),
            now.plusMillis(10));

    // Act: Execute both evaluations concurrently at the service layer
    CompletableFuture<Transaction> future1 =
        CompletableFuture.supplyAsync(() -> transactionEvaluationService.evaluate(tx1));
    CompletableFuture<Transaction> future2 =
        CompletableFuture.supplyAsync(() -> transactionEvaluationService.evaluate(tx2));

    // Wait for both to complete
    CompletableFuture.allOf(future1, future2).join();

    Transaction resp1 = future1.join();
    Transaction resp2 = future2.join();

    // Assert: One MUST be APPROVED, and the other MUST be REJECTED
    long approvedCount =
        Stream.of(resp1, resp2).filter(r -> TransactionStatus.APPROVED.equals(r.status())).count();

    long flaggedCount =
        Stream.of(resp1, resp2).filter(r -> TransactionStatus.FLAGGED.equals(r.status())).count();

    /*
     * EXPECTED BUSINESS BEHAVIOR:
     * Exactly 1 transaction is APPROVED (60k) and 1 transaction is FLAGGED (exceeds 100k daily limit).
     */
    assertThat(approvedCount).as("Exactly one transaction should be APPROVED").isEqualTo(1);

    assertThat(flaggedCount)
        .as("Exactly one transaction should be REJECTED due to 100k daily limit")
        .isEqualTo(1);
  }
}
