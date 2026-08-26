package io.github.anakidkin.aml.scheduler;


import io.github.anakidkin.aml.domain.OutboxStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import io.github.anakidkin.aml.service.impl.OutboxEventServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

  private static final int MAX_RETRIES = 3;

  private final OutboxEventServiceImpl outboxEventService;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Scheduled(fixedDelayString = "${aml.outbox.relay.delay-ms:1000}")
  public void publishPendingEvents() {
    List<OutboxEventEntity> pendingEvents = outboxEventService.find50ByStatus(OutboxStatus.PENDING);

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("Found {} pending outbox events for publishing", pendingEvents.size());

    for (OutboxEventEntity event : pendingEvents) {
      processSingleEvent(event);
    }
  }

  private void processSingleEvent(OutboxEventEntity event) {
    String topic = "aml." + event.getAggregateType().toLowerCase() + "-evaluated.v1";
    try {
      kafkaTemplate
          .send(topic, event.getAggregateId().toString(), event.getPayload())
          .get(3, TimeUnit.SECONDS);
      event.setStatus(OutboxStatus.PUBLISHED);
      outboxEventService.save(event);
    } catch (Exception e) {
      log.error("Failed to publish outbox event {}", event.getId(), e);
      int nextRetry = event.getRetryCount() + 1;
      event.setRetryCount(nextRetry);
      if (nextRetry >= MAX_RETRIES) {
        event.setStatus(OutboxStatus.FAILED);
      } else {
        event.setStatus(OutboxStatus.PENDING);
      }
      outboxEventService.save(event);
    }
  }
}