package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.domain.OutboxStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

  private final JpaOutboxRepository outboxRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Override
  public void save(OutboxEventEntity event) {
    outboxRepository.save(event);
  }

  @Override
  public List<OutboxEventEntity> find50ByStatus(OutboxStatus status) {
    return outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(status);
  }
}