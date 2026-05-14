package io.pravah.pipeline.domain.repository;

import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.pipeline.domain.Pipeline;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository interface for Pipeline aggregates.
 *
 * <p>This interface defines the contract for persistence operations on Pipeline aggregates. It
 * follows the Repository pattern from DDD, abstracting the data access layer from the domain.
 *
 * @see <a href="../../../../../../../docs/lld/01-design-patterns.md">Design Patterns -
 *     Repository</a>
 */
public interface PipelineRepository {

  /**
   * Finds a pipeline by ID within the current tenant context.
   *
   * @param id the pipeline ID
   * @return optional pipeline if found
   */
  Optional<Pipeline> findById(PipelineId id);

  /**
   * Finds a pipeline by ID and tenant ID.
   *
   * @param id the pipeline ID
   * @param tenantId the tenant ID (UUID)
   * @return optional pipeline if found
   */
  Optional<Pipeline> findByIdAndTenantId(PipelineId id, UUID tenantId);

  /**
   * Saves (creates or updates) a pipeline.
   *
   * @param pipeline the pipeline to save
   * @return the saved pipeline
   */
  Pipeline save(Pipeline pipeline);

  /**
   * Finds all pipelines in a project, excluding archived.
   *
   * @param projectId the project ID
   * @param pageable pagination parameters
   * @return page of pipelines
   */
  Page<Pipeline> findActiveByProjectId(ProjectId projectId, Pageable pageable);

  /**
   * Finds all pipelines in a project with a specific status.
   *
   * @param projectId the project ID
   * @param status the status to filter by
   * @param pageable pagination parameters
   * @return page of pipelines
   */
  Page<Pipeline> findByProjectIdAndStatus(ProjectId projectId, String status, Pageable pageable);

  /**
   * Checks if a pipeline with the given name exists in the project.
   *
   * @param projectId the project ID
   * @param name the pipeline name
   * @return true if exists
   */
  boolean existsByProjectIdAndName(ProjectId projectId, String name);

  /**
   * Deletes a pipeline (hard delete).
   *
   * @param pipeline the pipeline to delete
   */
  void delete(Pipeline pipeline);
}
