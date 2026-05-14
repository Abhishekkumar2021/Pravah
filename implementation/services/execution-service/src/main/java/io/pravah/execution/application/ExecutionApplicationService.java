package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for execution management.
 *
 * <p>Orchestrates the creation and management of pipeline executions. Business logic for state
 * transitions is delegated to the entity domain methods.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Execution</a>
 */
@Service
public class ExecutionApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionApplicationService.class);

  public static final String TRIGGER_MANUAL = "manual";

  private final PipelineCatalog pipelineCatalog;
  private final ExecutionEntityRepository executionEntityRepository;
  private final JobEntityRepository jobEntityRepository;
  private final EntityManager entityManager;

  public ExecutionApplicationService(
      PipelineCatalog pipelineCatalog,
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      EntityManager entityManager) {
    this.pipelineCatalog = pipelineCatalog;
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.entityManager = entityManager;
  }

  /**
   * Creates a manual execution for a published pipeline version.
   *
   * @param request the execution request containing pipeline ID and optional version
   * @param authorizationHeader the authorization header for pipeline catalog lookup
   * @return the created execution response with job details
   * @throws IllegalArgumentException if pipeline is not active or has no executable stages
   */
  @Transactional
  public CreateExecutionResponse startManualExecution(
      CreateExecutionRequest request, String authorizationHeader) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    PublishedPipelineSnapshot snapshot =
        pipelineCatalog.resolve(
            request.pipelineId(), request.pipelineVersion(), authorizationHeader);

    if (!"active".equalsIgnoreCase(snapshot.pipelineStatus())) {
      throw new IllegalArgumentException(
          "Pipeline must be active to run; current status: " + snapshot.pipelineStatus());
    }

    List<StagePlanner.PlannedJob> planned = StagePlanner.plan(snapshot.definition());
    if (planned.isEmpty()) {
      throw new IllegalArgumentException("Pipeline definition has no executable stages");
    }

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(snapshot.pipelineId())
            .pipelineVersion(snapshot.pipelineVersion())
            .triggerType(TRIGGER_MANUAL)
            .triggeredBy(userId)
            .parameters(Map.of())
            .build();

    execution = executionEntityRepository.save(execution);
    entityManager.flush();

    List<CreateExecutionResponse.JobResponse> jobResponses = new ArrayList<>();
    for (StagePlanner.PlannedJob pj : planned) {
      JobEntity job =
          JobEntity.builder()
              .executionId(execution.getId())
              .stageId(pj.stageId())
              .stageName(pj.stageName())
              .build();

      job = jobEntityRepository.save(job);
      jobResponses.add(
          new CreateExecutionResponse.JobResponse(
              job.getId(),
              job.getStageId(),
              job.getStageName(),
              job.getStatus().asDatabaseValue()));
    }

    log.info(
        "Manual execution created",
        kv("tenant_id", tenantId),
        kv("execution_id", execution.getId()),
        kv("pipeline_id", execution.getPipelineId()),
        kv("pipeline_version", execution.getPipelineVersion()),
        kv("job_count", jobResponses.size()));

    return new CreateExecutionResponse(
        execution.getId(),
        execution.getPipelineId(),
        execution.getPipelineVersion(),
        execution.getStatus().asDatabaseValue(),
        jobResponses);
  }

  private static UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  private static UUID requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("Missing user context");
    }
    return userId;
  }
}
