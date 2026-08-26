package io.github.anakidkin.aml.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(JacksonConfig.class)
class OutboxContractTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Outbox payload serialization & Listener deserialization full roundtrip")
  void shouldCorrectlySerializeAndDeserializeOutboxPayload() throws Exception {
    UUID txId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    Instant createdAt = Instant.parse("2026-08-22T20:00:00Z");
    Instant evaluatedAt = Instant.parse("2026-08-22T20:00:01Z");

    TransactionEvaluatedEvent originalEvent = new TransactionEvaluatedEvent(
        txId,
        "ACC_SENDER",
        "ACC_RECEIVER",
        new BigDecimal("15000.00"),
        "USD",
        "5411",
        false,
        "FLAGGED",
        List.of(
            new RuleResult("HARD_DAILY_VOLUME_EXCEEDED", 0, RuleStatus.FLAGGED, "Limit exceeded", 4L, true)
        ),
        createdAt,
        evaluatedAt
    );

    String payloadJson = objectMapper.writeValueAsString(originalEvent);

    assertThat(payloadJson)
        .contains("\"transactionId\":\"123e4567-e89b-12d3-a456-426614174000\"")
        .contains("\"accountFrom\":\"ACC_SENDER\"")
        .contains("\"accountTo\":\"ACC_RECEIVER\"")
        .contains("\"amount\":15000.00")
        .contains("\"currency\":\"USD\"")
        .contains("\"status\":\"FLAGGED\"");

    TransactionEvaluatedEvent deserializedEvent = objectMapper.readValue(payloadJson, TransactionEvaluatedEvent.class);

    assertThat(deserializedEvent).isEqualTo(originalEvent);
    assertThat(deserializedEvent.amount()).isEqualByComparingTo("15000.00");
    assertThat(deserializedEvent.createdAt()).isEqualTo(createdAt);
    assertThat(deserializedEvent.evaluatedAt()).isEqualTo(evaluatedAt);
  }
}