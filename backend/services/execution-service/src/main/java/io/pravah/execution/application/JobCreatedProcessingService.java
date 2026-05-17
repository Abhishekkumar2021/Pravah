package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.StageOutputSizeEnforcement;
import io.pravah.common.domain.StageOutputSizeGuard;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.ProcessedEventEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.persistence.repository.ProcessedEventRepository;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimeEvents;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code job.created} (LLD §3): assigns embedded runner, executes stage, advances DAG,
 * completes execution when all jobs finish.
 *
 * <p>Idempotent via {@link ProcessedEventRepository} keyed by {@code eventId} in the payload.
 */
@Service
public class JobCreatedProcessingService {

  private static final Logger log = LoggerFactory.getLogger(JobCreatedProcessingService.class);

  private static final String AGGREGATE_JOB = "job";

  private final ExecutionEntityRepository executionEntityRepository;
  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final StageExecutorRouter stageExecutorRouter;
  private final JobLogService jobLogService;
  private final JobFailureService jobFailureService;
  private final CheckpointService checkpointService;
  private final ExecutionJobQueueingService executionJobQueueingService;
  private final String jobCreatedTopic;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ObjectMapper objectMapper;
  private final long stageOutputMaxBytes;
  private final long stageOutputWarnBytes;

  public JobCreatedProcessingService(
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      ProcessedEventRepository processedEventRepository,
      StageExecutorRouter stageExecutorRouter,
      JobLogService jobLogService,
      JobFailureService jobFailureService,
      CheckpointService checkpointService,
      ExecutionJobQueueingService executionJobQueueingService,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper,
      @Value("${pravah.stage.max-output-bytes:1048576}") long stageOutputMaxBytes,
      @Value("${pravah.stage.output-warn-bytes:102400}") long stageOutputWarnBytes) {
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.processedEventRepository = processedEventRepository;
    this.stageExecutorRouter = stageExecutorRouter;
    this.jobLogService = jobLogService;
    this.jobFailureService = jobFailureService;
    this.checkpointService = checkpointService;
    this.executionJobQueueingService = executionJobQueueingService;
    this.jobCreatedTopic = jobCreatedTopic;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
    this.stageOutputMaxBytes = stageOutputMaxBytes;
    this.stageOutputWarnBytes = stageOutputWarnBytes;
  }

  @Transactional
  public void processJobCreated(Map<String, Object> payload) {
    UUID eventId = requireUuid(payload, "eventId");

    if (processedEventRepository.existsByEventId(eventId)) {
      log.debug("Duplicate job.created, skipping", kv("event_id", eventId));
      return;
    }

    UUID tenantId = requireUuid(payload, "tenantId");
    UUID contextTenant = TenantContext.getCurrentTenantId();
    if (contextTenant == null || !contextTenant.equals(tenantId)) {
      throw new IllegalStateException("TenantContext must match payload tenantId");
    }

    UUID jobId = requireUuid(payload, "jobId");
    JobEntity job =
        jobEntityRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Job not found for job.created"));

    if (job.getStatus() != JobState.QUEUED) {
      log.warn(
          "job.created for job not in QUEUED; acknowledging",
          kv("job_id", jobId),
          kv("status", job.getStatus()));
      recordProcessed(eventId);
      return;
    }

    ExecutionEntity execution =
        executionEntityRepository
            .findById(job.getExecutionId())
            .orElseThrow(() -> new IllegalStateException("Execution not found for job"));

    if (!execution.getTenantId().equals(tenantId)) {
      throw new IllegalStateException("Execution tenant mismatch for job.created");
    }

    ExecutionState executionStatusBeforeJob = execution.getStatus();

    job.assign(EmbeddedRunnerIds.LOCAL);
    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "Starting stage %s (attempt %d)".formatted(job.getStageName(), job.getAttempt()));

    EmbeddedStageExecutor.StageExecutionResult result = stageExecutorRouter.execute(job, execution);

    job =
        jobEntityRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Job not found after stage execution"));
    if (job.getStatus() != JobState.RUNNING) {
      log.info(
          "Job state changed during stage execution; skipping post-process",
          kv("job_id", jobId),
          kv("status", job.getStatus()));
      recordProcessed(eventId);
      publishExecutionStatusIfChanged(executionStatusBeforeJob, execution, tenantId);
      return;
    }

    if (result.exitCode() != 0) {
      String errorMessage =
          "Stage %s failed with exit code %d".formatted(job.getStageName(), result.exitCode());
      jobFailureService.handleStageFailure(
          execution,
          job,
          tenantId,
          result.exitCode(),
          errorMessage,
          enforceStageOutput(result.output(), job.getId(), job.getStageId()));
      recordProcessed(eventId);
      publishExecutionStatusIfChanged(executionStatusBeforeJob, execution, tenantId);
      return;
    }

    job.succeed(
        result.exitCode(),
        enforceStageOutput(result.output(), job.getId(), job.getStageId()),
        null);
    checkpointService.saveAfterJobSuccess(job);
    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "Stage %s completed successfully".formatted(job.getStageName()));

    Map<String, Object> definition = resolveDefinitionSnapshot(execution);
    Instant occurredAt = Instant.now();
    if (definition != null) {
      executionJobQueueingService.queueReadyJobs(execution, definition, occurredAt);
    } else {
      log.warn(
          "Missing pipeline definition snapshot on execution; cannot schedule downstream jobs",
          kv("execution_id", execution.getId()));
    }

    jobFailureService.finalizeExecutionIfDone(execution);
    recordProcessed(eventId);
    publishExecutionStatusIfChanged(executionStatusBeforeJob, execution, tenantId);

    log.info(
        "Job completed in embedded worker",
        kv("job_id", jobId),
        kv("execution_id", execution.getId()),
        kv("stage_id", job.getStageId()));
  }

  private void recordProcessed(UUID eventId) {
    processedEventRepository.save(
        new ProcessedEventEntity(eventId, JobEventTypes.JOB_CREATED, Instant.now()));
  }

  private static Map<String, Object> resolveDefinitionSnapshot(ExecutionEntity execution) {
    return execution.getDefinitionSnapshot();
  }

  private static UUID requireUuid(Map<String, Object> payload, String key) {
    Object v = payload.get(key);
    if (v == null) {
      throw new IllegalArgumentException("Missing required field: " + key);
    }
    return UUID.fromString(v.toString());
  }

  private void publishExecutionStatusIfChanged(
      ExecutionState statusBefore, ExecutionEntity execution, UUID tenantId) {
    if (execution.getStatus() == statusBefore) {
      return;
    }
    ExecutionRealtimeEvents.publishExecutionUpdated(
        applicationEventPublisher,
        objectMapper,
        tenantId,
        execution.getId(),
        execution.getStatus().asDatabaseValue(),
        Instant.now(),
        execution.getPipelineId());
  }

  private Map<String, Object> enforceStageOutput(
      Map<String, Object> output, UUID jobId, String stageId) {
    StageOutputSizeEnforcement enforced =
        StageOutputSizeGuard.enforce(
            output, stageOutputMaxBytes, stageOutputWarnBytes, objectMapper);
    logStageOutputSize(enforced, jobId, stageId);
    return enforced.output();
  }

  private void logStageOutputSize(StageOutputSizeEnforcement enforced, UUID jobId, String stageId) {
    if (enforced.serializationFailed()) {
      log.error(
          "Failed to measure stage output size; persisted truncated summary",
          kv("job_id", jobId),
          kv("stage_id", stageId));
      return;
    }
    if (enforced.truncated()) {
      log.warn(
          "Stage output truncated — exceeds max size",
          kv("job_id", jobId),
          kv("stage_id", stageId),
          kv("output_bytes", enforced.serializedBytes()),
          kv("max_bytes", stageOutputMaxBytes));
      return;
    }
    if (enforced.exceededWarnThreshold()) {
      log.warn(
          "Stage output exceeds warn threshold",
          kv("job_id", jobId),
          kv("stage_id", stageId),
          kv("output_bytes", enforced.serializedBytes()),
          kv("warn_bytes", stageOutputWarnBytes));
    }
  }
}
