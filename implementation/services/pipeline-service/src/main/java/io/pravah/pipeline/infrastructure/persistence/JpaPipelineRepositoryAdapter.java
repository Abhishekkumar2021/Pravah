package io.pravah.pipeline.infrastructure.persistence;

import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.common.domain.UserId;
import io.pravah.pipeline.domain.Pipeline;
import io.pravah.pipeline.domain.PipelineState;
import io.pravah.pipeline.domain.repository.PipelineRepository;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.JpaPipelineRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of the domain PipelineRepository.
 *
 * <p>This adapter translates between the domain Pipeline aggregate and the JPA PipelineEntity. It
 * follows the Repository pattern from DDD.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Repository</a>
 */
@Repository
public class JpaPipelineRepositoryAdapter implements PipelineRepository {

  private final JpaPipelineRepository jpaRepository;

  public JpaPipelineRepositoryAdapter(JpaPipelineRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Optional<Pipeline> findByIdAndTenantId(PipelineId id, UUID tenantId) {
    return jpaRepository.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
  }

  @Override
  public Pipeline save(Pipeline pipeline) {
    PipelineEntity entity =
        jpaRepository
            .findByIdAndTenantId(pipeline.getId().value(), pipeline.getTenantId())
            .map(
                existing -> {
                  applyPipelineFields(existing, pipeline);
                  return existing;
                })
            .orElseGet(() -> toEntity(pipeline));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Page<Pipeline> findActiveByProjectId(
      ProjectId projectId, UUID tenantId, Pageable pageable) {
    return jpaRepository
        .findActiveByProjectId(projectId.value(), tenantId, pageable)
        .map(this::toDomain);
  }

  @Override
  public Page<Pipeline> findByProjectIdAndStatus(
      ProjectId projectId, String status, UUID tenantId, Pageable pageable) {
    return jpaRepository
        .findByProjectIdAndStatus(projectId.value(), status, tenantId, pageable)
        .map(this::toDomain);
  }

  @Override
  public boolean existsByProjectIdAndName(ProjectId projectId, String name, UUID tenantId) {
    return jpaRepository.existsByProjectIdAndName(projectId.value(), name, tenantId);
  }

  @Override
  public void delete(Pipeline pipeline) {
    jpaRepository.deleteById(pipeline.getId().value());
  }

  private Pipeline toDomain(PipelineEntity entity) {
    return Pipeline.builder()
        .id(PipelineId.of(entity.getId()))
        .tenantId(entity.getTenantId())
        .projectId(ProjectId.of(entity.getProjectId()))
        .name(entity.getName())
        .description(entity.getDescription())
        .currentVersion(entity.getCurrentVersion())
        .state(PipelineState.fromDatabase(entity.getStatus()))
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .createdBy(UserId.of(entity.getCreatedBy()))
        .build();
  }

  private PipelineEntity toEntity(Pipeline pipeline) {
    return new PipelineEntity(
        pipeline.getId().value(),
        pipeline.getTenantId(),
        pipeline.getProjectId().value(),
        pipeline.getName(),
        pipeline.getDescription(),
        pipeline.getCurrentVersion(),
        pipeline.getState().asDatabaseValue(),
        pipeline.getCreatedAt(),
        pipeline.getUpdatedAt(),
        pipeline.getCreatedBy().value());
  }

  private void applyPipelineFields(PipelineEntity entity, Pipeline pipeline) {
    entity.setName(pipeline.getName());
    entity.setDescription(pipeline.getDescription());
    entity.setCurrentVersion(pipeline.getCurrentVersion());
    entity.setStatus(pipeline.getState().asDatabaseValue());
    entity.setUpdatedAt(pipeline.getUpdatedAt());
  }
}
