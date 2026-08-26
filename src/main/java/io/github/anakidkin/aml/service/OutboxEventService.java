package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.domain.OutboxStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;

import java.util.List;

public interface OutboxEventService {

  void save(OutboxEventEntity event);

  List<OutboxEventEntity> find50ByStatus(OutboxStatus status);
}
