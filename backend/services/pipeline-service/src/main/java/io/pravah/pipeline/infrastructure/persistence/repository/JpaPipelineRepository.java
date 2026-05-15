package io.pravah.pipeline.infrastructure.persistence.repository;

import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for PipelineEntity.
 *
 * <p>This is the low-level data access interface. Application code should use the domain {@link
 * io.pravah.pipeline.domain.repository.PipelineRepository} interface instead.
 *
 * <p>Read queries include an explicit {@code tenantId} predicate (defense in depth) in addition to
 * PostgreSQL RLS (ADR-013).
 */
public interface JpaPipelineRepository extends JpaRepository<PipelineEntity, UUID> {

  Optional<PipelineEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  @Query(
      "SELECT p FROM PipelineEntity p WHERE p.projectId = :projectId AND p.status <> 'archived' AND"
          + " p.tenantId = :tenantId")
  Page<PipelineEntity> findActiveByProjectId(
      @Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(
      "SELECT p FROM PipelineEntity p WHERE p.projectId = :projectId AND p.status = :status AND"
          + " p.tenantId = :tenantId")
  Page<PipelineEntity> findByProjectIdAndStatus(
      @Param("projectId") UUID projectId,
      @Param("status") String status,
      @Param("tenantId") UUID tenantId,
      Pageable pageable);

  @Query(
      "SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PipelineEntity p WHERE"
          + " p.projectId = :projectId AND p.name = :name AND p.tenantId = :tenantId")
  boolean existsByProjectIdAndName(
      @Param("projectId") UUID projectId,
      @Param("name") String name,
      @Param("tenantId") UUID tenantId);
}
