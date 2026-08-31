package io.github.anakidkin.aml.repository;

import io.github.anakidkin.aml.entity.OutboxEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {}
