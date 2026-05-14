package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.api.dto.GetExecutionResponse;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

  private static final String AGGREGATE_EXECUTION = "execution";

  private final PipelineCatalog pipelineCatalog;
  private final ExecutionEntityRepository executionEntityRepository;
  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final EntityManager entityManager;
  private final String executionEventsTopic;

  public ExecutionApplicationService(
      PipelineCatalog pipelineCatalog,
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      EntityManager entityManager,
      @Value("${pravah.outbox.topic.execution-events}") String executionEventsTopic) {
    this.pipelineCatalog = pipelineCatalog;
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.entityManager = entityManager;
    this.executionEventsTopic = executionEventsTopic;
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

    List<String> rootStageIds = StagePlanner.rootStageIds(snapshot.definition());
    if (rootStageIds.isEmpty()) {
      throw new IllegalStateException("Pipeline has no root stages for scheduling");
    }

    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.now();
    Map<String, Object> outboxPayload =
        buildExecutionCreatedPayload(
            eventId,
            occurredAt,
            tenantId,
            execution,
            snapshot.pipelineId(),
            snapshot.pipelineVersion(),
            rootStageIds);

    outboxRepository.save(
        new OutboxEntity(
            AGGREGATE_EXECUTION,
            execution.getId(),
            ExecutionEventTypes.EXECUTION_CREATED,
            executionEventsTopic,
            execution.getId().toString(),
            outboxPayload,
            occurredAt));

    log.info(
        "Manual execution created",
        kv("tenant_id", tenantId),
        kv("execution_id", execution.getId()),
        kv("pipeline_id", execution.getPipelineId()),
        kv("pipeline_version", execution.getPipelineVersion()),
        kv("job_count", jobResponses.size()),
        kv("event_id", eventId));

    return new CreateExecutionResponse(
        execution.getId(),
        execution.getPipelineId(),
        execution.getPipelineVersion(),
        execution.getStatus().asDatabaseValue(),
        jobResponses);
  }

  private static Map<String, Object> buildExecutionCreatedPayload(
      UUID eventId,
      Instant occurredAt,
      UUID tenantId,
      ExecutionEntity execution,
      UUID pipelineId,
      int pipelineVersion,
      List<String> rootStageIds) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", eventId.toString());
    m.put("eventType", ExecutionEventTypes.EXECUTION_CREATED);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", AGGREGATE_EXECUTION);
    m.put("aggregateId", execution.getId().toString());
    m.put("tenantId", tenantId.toString());
    m.put("executionId", execution.getId().toString());
    m.put("pipelineId", pipelineId.toString());
    m.put("pipelineVersion", pipelineVersion);
    m.put("triggerType", execution.getTriggerType());
    if (execution.getTriggeredBy() != null) {
      m.put("triggeredBy", execution.getTriggeredBy().toString());
    }
    m.put("rootStageIds", rootStageIds);
    return m;
  }

  /**
   * Returns an execution and its jobs for the current tenant (RLS-enforced).
   *
   * <p>Also verifies {@link ExecutionEntity#getTenantId()} matches {@link TenantContext} so tenant
   * isolation holds when the DB role bypasses RLS (e.g. PostgreSQL superuser in local tests).
   *
   * @param executionId the execution id
   * @return execution details
   * @throws EntityNotFoundException if not found or not visible for this tenant
   */
  @Transactional(readOnly = true)
  public GetExecutionResponse getExecution(UUID executionId) {
    UUID tenantId = requireTenantId();
    ExecutionEntity execution =
        executionEntityRepository
            .findById(executionId)
            .orElseThrow(() -> new EntityNotFoundException("Execution", executionId));

    if (!execution.getTenantId().equals(tenantId)) {
      throw new EntityNotFoundException("Execution", executionId);
    }

    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
    List<GetExecutionResponse.JobSummary> jobSummaries =
        jobs.stream()
            .map(
                j ->
                    new GetExecutionResponse.JobSummary(
                        j.getId(),
                        j.getStageId(),
                        j.getStageName(),
                        j.getStatus().asDatabaseValue(),
                        j.getAttempt()))
            .toList();

    return new GetExecutionResponse(
        execution.getId(),
        execution.getPipelineId(),
        execution.getPipelineVersion(),
        execution.getStatus().asDatabaseValue(),
        execution.getTriggerType(),
        execution.getTriggeredBy(),
        execution.getCreatedAt(),
        jobSummaries);
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
