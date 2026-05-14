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
 */
public interface JpaPipelineRepository extends JpaRepository<PipelineEntity, UUID> {

  Optional<PipelineEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<PipelineEntity> findByProjectId(UUID projectId, Pageable pageable);

  Page<PipelineEntity> findByProjectIdAndStatus(UUID projectId, String status, Pageable pageable);

  @Query("SELECT p FROM PipelineEntity p WHERE p.projectId = :projectId AND p.status != 'archived'")
  Page<PipelineEntity> findActiveByProjectId(@Param("projectId") UUID projectId, Pageable pageable);

  boolean existsByProjectIdAndName(UUID projectId, String name);
}
