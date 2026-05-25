package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.TriggerExecutionIdempotencyEntity;
import io.pravah.execution.infrastructure.persistence.entity.TriggerExecutionIdempotencyId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerExecutionIdempotencyRepository
    extends JpaRepository<TriggerExecutionIdempotencyEntity, TriggerExecutionIdempotencyId> {

  Optional<TriggerExecutionIdempotencyEntity> findByTenantIdAndIdempotencyKey(
      UUID tenantId, String idempotencyKey);
}
