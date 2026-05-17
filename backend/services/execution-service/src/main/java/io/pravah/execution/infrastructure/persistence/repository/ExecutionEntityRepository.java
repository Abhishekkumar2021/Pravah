package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.common.domain.ExecutionState;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionEntityRepository extends JpaRepository<ExecutionEntity, UUID> {

  /**
   * Paginated query for executions visible to a tenant with optional filters.
   *
   * <p>Performance note: Indexes on {@code executions} support tenant and filter patterns; see
   * {@code V1__init_execution_schema.sql} ({@code idx_executions_tenant_status}, {@code
   * idx_executions_pipeline}, {@code idx_executions_created}).
   */
  @Query(
      """
      SELECT e FROM ExecutionEntity e
      WHERE e.tenantId = :tenantId
        AND (:status IS NULL OR e.status = :status)
        AND (:pipelineId IS NULL OR e.pipelineId = :pipelineId)
      """)
  Page<ExecutionEntity> findForTenant(
      @Param("tenantId") UUID tenantId,
      @Param("status") ExecutionState status,
      @Param("pipelineId") UUID pipelineId,
      Pageable pageable);

  long countByRetryOf(UUID retryOf);
}
