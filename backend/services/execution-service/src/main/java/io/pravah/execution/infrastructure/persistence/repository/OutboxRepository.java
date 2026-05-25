package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

  /**
   * Fetches unpublished outbox rows that are ready to publish (createdAt <= now). Rows with
   * future-dated createdAt (e.g., delayed retries) are excluded until their scheduled time.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      "SELECT o FROM OutboxEntity o WHERE o.publishedAt IS NULL AND o.retryCount < "
          + io.pravah.execution.infrastructure.outbox.OutboxPublishPolicy.MAX_PUBLISH_RETRIES
          + " AND o.createdAt <= :now ORDER BY o.createdAt ASC")
  List<OutboxEntity> findUnpublishedForUpdate(@Param("now") Instant now, Pageable pageable);
}
