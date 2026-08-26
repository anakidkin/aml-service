package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.domain.OutboxStatus;
import io.github.anakidkin.aml.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {
  List<OutboxEventEntity> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}