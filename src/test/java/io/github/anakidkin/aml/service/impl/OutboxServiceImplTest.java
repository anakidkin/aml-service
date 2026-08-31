package io.github.anakidkin.aml.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import io.github.anakidkin.aml.entity.TransactionEntity;
import io.github.anakidkin.aml.mapper.TransactionDomainMapper;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

  @Mock
  private JpaTransactionRepository transactionRepository;

  @Mock
  private JpaOutboxRepository jpaOutboxRepository;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private TransactionDomainMapper transactionDomainMapper;

  @Captor
  private ArgumentCaptor<OutboxEventEntity> outboxCaptor;

  @InjectMocks
  private OutboxServiceImpl outboxService;

  @Test
  @DisplayName("Should save transaction entity and outbox event successfully")
  void shouldSaveTransactionAndOutboxEvent() throws Exception {
    Transaction tx = createTransaction();
    List<RuleResult> ruleResults = List.of();

    when(transactionDomainMapper.toEntity(tx)).thenReturn(new TransactionEntity());
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"" + tx.id() + "\"}");

    Transaction savedTx = outboxService.save(tx, ruleResults);

    assertThat(savedTx).isEqualTo(tx);

    verify(transactionRepository).save(any(TransactionEntity.class));
    verify(jpaOutboxRepository).save(outboxCaptor.capture());

    OutboxEventEntity outboxEvent = outboxCaptor.getValue();
    assertThat(outboxEvent.getAggregateId()).isEqualTo(tx.id());
    assertThat(outboxEvent.getPayload()).contains(tx.id().toString());
  }

  @Test
  @DisplayName("Should throw IllegalStateException when serialization fails")
  void shouldThrowIllegalStateExceptionOnJsonError() throws Exception {
    Transaction tx = createTransaction();
    when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON Error") {
    });

    assertThatThrownBy(() -> outboxService.save(tx, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to serialize transaction payload for outbox");
  }

  private Transaction createTransaction() {
    return new Transaction(
        UUID.randomUUID(), "ACC_1", "ACC_2",
        new Money(new BigDecimal("100"), "USD"), "5411", false,
        TransactionStatus.APPROVED, null, Instant.now(), Instant.now()
    );
  }
}