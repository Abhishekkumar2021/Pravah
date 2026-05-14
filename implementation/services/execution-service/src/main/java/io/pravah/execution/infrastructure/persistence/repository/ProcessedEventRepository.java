package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.ProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for idempotent consumer deduplication (LLD §16).
 *
 * <p>Used to track which event IDs have already been processed, preventing duplicate handling on
 * Kafka redelivery.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {
  boolean existsByEventId(UUID eventId);
}
