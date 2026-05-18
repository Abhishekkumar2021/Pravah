package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.PipelineDefinitionResolver;
import io.pravah.common.domain.RetryPolicy;
import io.pravah.common.domain.RetryPolicyParser;
import io.pravah.common.exception.AccessDeniedException;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.api.dto.GetExecutionResponse;
import io.pravah.execution.api.dto.ListExecutionsResponse;
import io.pravah.execution.api.dto.RetryExecutionRequest;
import io.pravah.execution.api.dto.TriggerPipelineRunRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.pipeline.InternalHttpPipelineCatalog;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimeEvents;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  public static final String TRIGGER_SCHEDULED = "scheduled";
  public static final String TRIGGER_API = "api";

  private static final String AGGREGATE_EXECUTION = "execution";

  private final PipelineCatalog pipelineCatalog;
  private final InternalHttpPipelineCatalog internalPipelineCatalog;
  private final ExecutionEntityRepository executionEntityRepository;
  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final EntityManager entityManager;
  private final CheckpointService checkpointService;
  private final ExecutionJobQueueingService executionJobQueueingService;
  private final String executionEventsTopic;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ObjectMapper objectMapper;

  public ExecutionApplicationService(
      PipelineCatalog pipelineCatalog,
      InternalHttpPipelineCatalog internalPipelineCatalog,
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      EntityManager entityManager,
      CheckpointService checkpointService,
      ExecutionJobQueueingService executionJobQueueingService,
      @Value("${pravah.outbox.topic.execution-events}") String executionEventsTopic,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper) {
    this.pipelineCatalog = pipelineCatalog;
    this.internalPipelineCatalog = internalPipelineCatalog;
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.entityManager = entityManager;
    this.checkpointService = checkpointService;
    this.executionJobQueueingService = executionJobQueueingService;
    this.executionEventsTopic = executionEventsTopic;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
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

    return materializeExecution(
        tenantId, userId, TRIGGER_MANUAL, snapshot, normalizeParameters(request.parameters()));
  }

  /**
   * Creates an API-triggered execution (US-03.08).
   *
   * @param pipelineId pipeline to run (from path)
   * @param request optional version, parameters, and async flag (handled by controller)
   * @param authorizationHeader forwarded to pipeline catalog lookup
   */
  @Transactional
  public CreateExecutionResponse startApiExecution(
      UUID pipelineId, TriggerPipelineRunRequest request, String authorizationHeader) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    PublishedPipelineSnapshot snapshot =
        pipelineCatalog.resolve(pipelineId, request.pipelineVersion(), authorizationHeader);

    return materializeExecution(
        tenantId, userId, TRIGGER_API, snapshot, normalizeParameters(request.parameters()));
  }

  @Transactional
  public CreateExecutionResponse startScheduledExecution(
      UUID tenantId, UUID pipelineId, UUID scheduleId) {
    PublishedPipelineSnapshot snapshot =
        internalPipelineCatalog.resolvePublished(tenantId, pipelineId);
    log.debug(
        "Resolved pipeline for scheduled run",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("schedule_id", scheduleId));
    return materializeExecution(tenantId, null, TRIGGER_SCHEDULED, snapshot, Map.of());
  }

  private static Map<String, Object> normalizeParameters(Map<String, Object> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  private CreateExecutionResponse materializeExecution(
      UUID tenantId,
      UUID triggeredBy,
      String triggerType,
      PublishedPipelineSnapshot snapshot,
      Map<String, Object> parameters) {
    if (!"active".equalsIgnoreCase(snapshot.pipelineStatus())) {
      throw new IllegalArgumentException(
          "Pipeline must be active to run; current status: " + snapshot.pipelineStatus());
    }

    UUID executionId = UUID.randomUUID();
    Instant materializedAt = Instant.now();
    Map<String, Object> resolvedDefinition =
        PipelineDefinitionResolver.resolveForExecution(
            snapshot.definition(),
            parameters,
            snapshot.pipelineId(),
            snapshot.pipelineVersion(),
            executionId,
            materializedAt);

    List<StagePlanner.PlannedJob> planned = StagePlanner.plan(resolvedDefinition);
    if (planned.isEmpty()) {
      throw new IllegalArgumentException("Pipeline definition has no executable stages");
    }

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(snapshot.pipelineId())
            .pipelineVersion(snapshot.pipelineVersion())
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .parameters(parameters)
            .definitionSnapshot(resolvedDefinition)
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
        "Execution created",
        kv("tenant_id", tenantId),
        kv("trigger_type", triggerType),
        kv("execution_id", execution.getId()),
        kv("pipeline_id", execution.getPipelineId()),
        kv("pipeline_version", execution.getPipelineVersion()),
        kv("job_count", jobResponses.size()),
        kv("event_id", eventId));

    ExecutionRealtimeEvents.publishExecutionUpdated(
        applicationEventPublisher,
        objectMapper,
        tenantId,
        execution.getId(),
        execution.getStatus().asDatabaseValue(),
        Instant.now(),
        execution.getPipelineId());

    return new CreateExecutionResponse(
        execution.getId(),
        execution.getPipelineId(),
        execution.getPipelineVersion(),
        execution.getStatus().asDatabaseValue(),
        jobResponses);
  }

  /**
   * Retries a failed execution from a specific stage (US-02.05 / US-02.12).
   *
   * <p>Creates a new execution with {@code retry_of} referencing the source. Upstream stages are
   * restored from checkpoints (or prior successful job output); the failed stage and downstream
   * stages are re-queued.
   */
  @Transactional
  public CreateExecutionResponse retryFromStage(
      UUID sourceExecutionId, RetryExecutionRequest request) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();
    String fromStageId = request.fromStageId().trim();

    ExecutionEntity source = loadExecutionForTenant(sourceExecutionId, tenantId);
    assertCanCancelAsUser(source, userId);

    if (source.getStatus() != ExecutionState.FAILED) {
      throw new InvalidStateTransitionException(
          "execution", source.getId().toString(), source.getStatus().asDatabaseValue(), "retry");
    }

    Map<String, Object> definition = source.getDefinitionSnapshot();
    if (definition == null || definition.isEmpty()) {
      throw new IllegalStateException("Execution has no definition snapshot for retry");
    }

    List<JobEntity> sourceJobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(sourceExecutionId);
    Map<String, JobEntity> sourceJobsByStage =
        sourceJobs.stream().collect(Collectors.toMap(JobEntity::getStageId, j -> j, (a, b) -> a));

    JobEntity failedJob = sourceJobsByStage.get(fromStageId);
    if (failedJob == null) {
      throw new IllegalArgumentException("Unknown stage id: " + fromStageId);
    }
    if (failedJob.getStatus() != JobState.FAILED) {
      throw new IllegalArgumentException(
          "Stage %s is not failed (status: %s)".formatted(fromStageId, failedJob.getStatus()));
    }

    Set<String> upstream = StagePlanner.transitiveUpstream(definition, fromStageId);
    for (String upstreamStageId : upstream) {
      JobEntity upstreamJob = sourceJobsByStage.get(upstreamStageId);
      if (upstreamJob == null || upstreamJob.getStatus() != JobState.SUCCEEDED) {
        throw new IllegalArgumentException(
            "Upstream stage %s did not succeed; cannot retry from %s"
                .formatted(upstreamStageId, fromStageId));
      }
    }

    source.markRetrying();

    ExecutionEntity retryExecution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(source.getPipelineId())
            .pipelineVersion(source.getPipelineVersion())
            .triggerType(TRIGGER_MANUAL)
            .triggeredBy(userId)
            .parameters(source.getParameters())
            .definitionSnapshot(definition)
            .retryOf(source.getId())
            .build();
    retryExecution = executionEntityRepository.save(retryExecution);
    entityManager.flush();

    List<JobEntity> retryJobs = new ArrayList<>();
    for (StagePlanner.PlannedJob planned : StagePlanner.plan(definition)) {
      JobEntity job =
          JobEntity.builder()
              .executionId(retryExecution.getId())
              .stageId(planned.stageId())
              .stageName(planned.stageName())
              .build();
      job = jobEntityRepository.save(job);

      if (upstream.contains(planned.stageId())) {
        restoreUpstreamJob(job, source, sourceJobsByStage, planned.stageId());
        checkpointService.saveAfterJobSuccess(job);
      }
      retryJobs.add(job);
    }
    entityManager.flush();

    Instant occurredAt = Instant.now();
    int queued = executionJobQueueingService.queueReadyJobs(retryExecution, definition, occurredAt);
    if (queued > 0) {
      retryExecution.start();
    }

    UUID eventId = UUID.randomUUID();
    Map<String, Object> outboxPayload =
        buildExecutionCreatedPayload(
            eventId,
            occurredAt,
            tenantId,
            retryExecution,
            source.getPipelineId(),
            source.getPipelineVersion(),
            List.of());
    outboxRepository.save(
        new OutboxEntity(
            AGGREGATE_EXECUTION,
            retryExecution.getId(),
            ExecutionEventTypes.EXECUTION_CREATED,
            executionEventsTopic,
            retryExecution.getId().toString(),
            outboxPayload,
            occurredAt));

    log.info(
        "Execution retry created",
        kv("tenant_id", tenantId),
        kv("source_execution_id", source.getId()),
        kv("retry_execution_id", retryExecution.getId()),
        kv("from_stage_id", fromStageId),
        kv("upstream_restored", upstream.size()),
        kv("jobs_queued", queued),
        kv("event_id", eventId));

    ExecutionRealtimeEvents.publishExecutionUpdated(
        applicationEventPublisher,
        objectMapper,
        tenantId,
        retryExecution.getId(),
        retryExecution.getStatus().asDatabaseValue(),
        Instant.now(),
        retryExecution.getPipelineId());

    List<CreateExecutionResponse.JobResponse> jobResponses =
        retryJobs.stream()
            .map(
                j ->
                    new CreateExecutionResponse.JobResponse(
                        j.getId(),
                        j.getStageId(),
                        j.getStageName(),
                        j.getStatus().asDatabaseValue()))
            .toList();

    return new CreateExecutionResponse(
        retryExecution.getId(),
        retryExecution.getPipelineId(),
        retryExecution.getPipelineVersion(),
        retryExecution.getStatus().asDatabaseValue(),
        jobResponses);
  }

  @Transactional
  public void clearCheckpoints(UUID executionId) {
    UUID tenantId = requireTenantId();
    loadExecutionForTenant(executionId, tenantId);
    checkpointService.clearForExecution(executionId);
    log.info("Checkpoints cleared manually", kv("execution_id", executionId));
  }

  private void restoreUpstreamJob(
      JobEntity targetJob,
      ExecutionEntity sourceExecution,
      Map<String, JobEntity> sourceJobsByStage,
      String stageId) {
    Map<String, Object> state =
        checkpointService
            .loadCheckpointState(sourceExecution.getId(), stageId)
            .orElseGet(
                () -> {
                  JobEntity sourceJob = sourceJobsByStage.get(stageId);
                  if (sourceJob == null || sourceJob.getStatus() != JobState.SUCCEEDED) {
                    throw new IllegalStateException(
                        "No checkpoint or succeeded job for " + stageId);
                  }
                  Map<String, Object> fallback = new LinkedHashMap<>();
                  fallback.put(
                      "exitCode", sourceJob.getExitCode() != null ? sourceJob.getExitCode() : 0);
                  fallback.put("output", sourceJob.getOutput());
                  fallback.put("artifacts", sourceJob.getArtifacts());
                  fallback.put("metrics", sourceJob.getMetrics());
                  return fallback;
                });

    int exitCode = state.get("exitCode") instanceof Number n ? n.intValue() : 0;
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) state.get("output");
    @SuppressWarnings("unchecked")
    Map<String, Object> artifacts = (Map<String, Object>) state.get("artifacts");
    @SuppressWarnings("unchecked")
    Map<String, Object> metrics = (Map<String, Object>) state.get("metrics");
    targetJob.restoreFromCheckpoint(exitCode, output, artifacts, metrics);
  }

  /**
   * Cancels a non-terminal execution (US-02.04). Transitions execution and all non-terminal jobs to
   * {@code CANCELLED}, and appends an {@code execution.cancelled} outbox row for relay.
   *
   * <p>Idempotent: if the execution is already {@link ExecutionState#CANCELLED}, returns the
   * current view without emitting another event.
   *
   * @param executionId the execution id
   * @return current execution snapshot including job statuses
   * @throws EntityNotFoundException if not found or not visible for this tenant
   * @throws InvalidStateTransitionException if the execution is in a terminal state other than
   *     cancelled
   * @throws AccessDeniedException if {@link ExecutionEntity#getTriggeredBy()} is non-null and does
   *     not match the current user (another user in the tenant may not cancel that run)
   */
  @Transactional
  public GetExecutionResponse cancelExecution(UUID executionId) {
    UUID tenantId = requireTenantId();
    UUID canceller = requireUserId();

    ExecutionEntity execution = loadExecutionForTenant(executionId, tenantId);
    assertCanCancelAsUser(execution, canceller);

    if (execution.getStatus() == ExecutionState.CANCELLED) {
      return toGetExecutionResponse(execution);
    }
    if (execution.getStatus() != ExecutionState.PENDING
        && execution.getStatus() != ExecutionState.RUNNING) {
      throw new InvalidStateTransitionException(
          "execution",
          execution.getId().toString(),
          execution.getStatus().asDatabaseValue(),
          "cancel");
    }

    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
    int jobsStopped = 0;
    for (JobEntity job : jobs) {
      if (!job.getStatus().isTerminal()) {
        job.cancel();
        jobsStopped++;
      }
    }

    execution.cancel();

    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.now();
    Map<String, Object> payload =
        buildExecutionCancelledPayload(eventId, occurredAt, tenantId, execution, canceller);
    outboxRepository.save(
        new OutboxEntity(
            AGGREGATE_EXECUTION,
            execution.getId(),
            ExecutionEventTypes.EXECUTION_CANCELLED,
            executionEventsTopic,
            execution.getId().toString(),
            payload,
            occurredAt));

    log.info(
        "Execution cancelled",
        kv("tenant_id", tenantId),
        kv("execution_id", execution.getId()),
        kv("event_id", eventId),
        kv("jobs_stopped", jobsStopped));

    ExecutionRealtimeEvents.publishExecutionUpdated(
        applicationEventPublisher,
        objectMapper,
        tenantId,
        execution.getId(),
        execution.getStatus().asDatabaseValue(),
        Instant.now(),
        execution.getPipelineId());

    return toGetExecutionResponse(execution, jobs);
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

  private static Map<String, Object> buildExecutionCancelledPayload(
      UUID eventId,
      Instant occurredAt,
      UUID tenantId,
      ExecutionEntity execution,
      UUID cancelledBy) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", eventId.toString());
    m.put("eventType", ExecutionEventTypes.EXECUTION_CANCELLED);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", AGGREGATE_EXECUTION);
    m.put("aggregateId", execution.getId().toString());
    m.put("tenantId", tenantId.toString());
    m.put("executionId", execution.getId().toString());
    m.put("pipelineId", execution.getPipelineId().toString());
    m.put("pipelineVersion", execution.getPipelineVersion());
    m.put("triggerType", execution.getTriggerType());
    if (execution.getTriggeredBy() != null) {
      m.put("triggeredBy", execution.getTriggeredBy().toString());
    }
    m.put("cancelledBy", cancelledBy.toString());
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
    ExecutionEntity execution = loadExecutionForTenant(executionId, tenantId);
    return toGetExecutionResponse(execution);
  }

  /**
   * Lists executions for the current tenant, newest first, with optional filters (US-12.07).
   *
   * @param statusFilter optional execution status (case-insensitive database value, e.g. {@code
   *     pending})
   * @param pipelineId optional pipeline id filter
   * @param page zero-based page index
   * @param size page size (clamped to 1..100)
   */
  @Transactional(readOnly = true)
  public ListExecutionsResponse listExecutions(
      String statusFilter, UUID pipelineId, int page, int size) {
    UUID tenantId = requireTenantId();
    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);
    PageRequest pageable =
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

    ExecutionState stateFilter = null;
    if (statusFilter != null && !statusFilter.isBlank()) {
      stateFilter = ExecutionState.fromDatabase(statusFilter.trim());
    }

    Page<ExecutionEntity> result =
        executionEntityRepository.findForTenant(tenantId, stateFilter, pipelineId, pageable);

    List<ListExecutionsResponse.ExecutionListItem> items =
        result.getContent().stream()
            .map(
                e ->
                    new ListExecutionsResponse.ExecutionListItem(
                        e.getId(),
                        e.getPipelineId(),
                        e.getPipelineVersion(),
                        e.getStatus().asDatabaseValue(),
                        e.getTriggerType(),
                        e.getTriggeredBy(),
                        e.getCreatedAt(),
                        e.getStartedAt(),
                        e.getCompletedAt()))
            .toList();

    return new ListExecutionsResponse(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.isLast());
  }

  private ExecutionEntity loadExecutionForTenant(UUID executionId, UUID tenantId) {
    ExecutionEntity execution =
        executionEntityRepository
            .findById(executionId)
            .orElseThrow(() -> new EntityNotFoundException("Execution", executionId));
    if (!execution.getTenantId().equals(tenantId)) {
      throw new EntityNotFoundException("Execution", executionId);
    }
    return execution;
  }

  /**
   * When {@code triggeredBy} is set, only that principal may cancel (manual runs and similar). When
   * it is null (e.g. system-triggered), any authenticated user in the tenant may cancel.
   */
  private static void assertCanCancelAsUser(ExecutionEntity execution, UUID canceller) {
    UUID triggeredBy = execution.getTriggeredBy();
    if (triggeredBy != null && !triggeredBy.equals(canceller)) {
      throw new AccessDeniedException("execution", "cancel");
    }
  }

  private GetExecutionResponse toGetExecutionResponse(ExecutionEntity execution) {
    return toGetExecutionResponse(
        execution, jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execution.getId()));
  }

  private GetExecutionResponse toGetExecutionResponse(
      ExecutionEntity execution, List<JobEntity> jobs) {
    Map<String, Object> definition = execution.getDefinitionSnapshot();
    List<GetExecutionResponse.JobSummary> jobSummaries =
        jobs.stream()
            .map(
                j -> {
                  RetryPolicy retry = resolveRetryPolicyForJob(definition, j.getStageId());
                  return new GetExecutionResponse.JobSummary(
                      j.getId(),
                      j.getStageId(),
                      j.getStageName(),
                      j.getStatus().asDatabaseValue(),
                      j.getAttempt(),
                      retry.maxAttempts(),
                      j.getQueuedAt(),
                      j.getStartedAt(),
                      j.getCompletedAt(),
                      j.getOutput());
                })
            .toList();
    long retryCount = executionEntityRepository.countByRetryOf(execution.getId());

    return new GetExecutionResponse(
        execution.getId(),
        execution.getPipelineId(),
        execution.getPipelineVersion(),
        execution.getStatus().asDatabaseValue(),
        execution.getTriggerType(),
        execution.getTriggeredBy(),
        execution.getRetryOf(),
        (int) retryCount,
        execution.getCreatedAt(),
        jobSummaries);
  }

  private static RetryPolicy resolveRetryPolicyForJob(
      Map<String, Object> definition, String stageId) {
    if (definition == null || definition.isEmpty()) {
      return RetryPolicy.DEFAULT;
    }
    return RetryPolicyParser.resolveForStage(definition, stageId);
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
