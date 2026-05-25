package io.pravah.scheduler.infrastructure.persistence.repository;

import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchPendingEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TriggerDispatchPendingRepository
    extends JpaRepository<TriggerDispatchPendingEntity, UUID> {

  @Query(
      """
      SELECT p FROM TriggerDispatchPendingEntity p
      WHERE p.status IN ('pending', 'failed')
        AND p.nextRetryAt <= :now
      ORDER BY p.nextRetryAt ASC
      """)
  List<TriggerDispatchPendingEntity> findDueForRetry(@Param("now") Instant now);

  boolean existsByTriggerIdAndStatusIn(UUID triggerId, Collection<String> statuses);

  boolean existsByTriggerIdAndIdempotencyKeyAndStatusIn(
      UUID triggerId, String idempotencyKey, Collection<String> statuses);
}
