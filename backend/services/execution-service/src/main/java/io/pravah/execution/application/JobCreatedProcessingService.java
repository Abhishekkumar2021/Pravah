package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.entity.ProcessedEventEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.persistence.repository.ProcessedEventRepository;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimeEvents;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
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
  private final EmbeddedStageExecutor embeddedStageExecutor;
  private final JobLogService jobLogService;
  private final String jobCreatedTopic;
  private final int maxJobAttempts;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ObjectMapper objectMapper;

  public JobCreatedProcessingService(
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      ProcessedEventRepository processedEventRepository,
      EmbeddedStageExecutor embeddedStageExecutor,
      JobLogService jobLogService,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic,
      @Value("${pravah.job.max-attempts:3}") int maxJobAttempts,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper) {
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.processedEventRepository = processedEventRepository;
    this.embeddedStageExecutor = embeddedStageExecutor;
    this.jobLogService = jobLogService;
    this.jobCreatedTopic = jobCreatedTopic;
    this.maxJobAttempts = maxJobAttempts;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
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

    EmbeddedStageExecutor.StageExecutionResult result =
        embeddedStageExecutor.execute(job, execution);
    if (result.exitCode() != 0) {
      JobState statusBeforeFail = job.getStatus();
      job.fail(result.exitCode(), "Embedded executor reported non-zero exit", maxJobAttempts);
      jobLogService.append(
          job.getId(),
          JobLogLevel.ERROR,
          "Stage %s failed with exit code %d".formatted(job.getStageName(), result.exitCode()));

      if (statusBeforeFail == JobState.RUNNING && job.getStatus() == JobState.QUEUED) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.WARN,
            "Scheduling retry (attempt %d of %d)".formatted(job.getAttempt(), maxJobAttempts));
        Instant retryOccurredAt = Instant.now();
        Map<String, Object> retryPayload =
            buildJobCreatedPayload(retryOccurredAt, tenantId, execution, job);
        outboxRepository.save(
            new OutboxEntity(
                AGGREGATE_JOB,
                job.getId(),
                JobEventTypes.JOB_CREATED,
                jobCreatedTopic,
                execution.getId().toString(),
                retryPayload,
                retryOccurredAt));
        log.info(
            "Scheduled job retry",
            kv("job_id", job.getId()),
            kv("attempt", job.getAttempt()),
            kv("max_attempts", maxJobAttempts));
      }

      finalizeExecutionIfDone(execution);
      recordProcessed(eventId);
      publishExecutionStatusIfChanged(executionStatusBeforeJob, execution, tenantId);
      return;
    }

    job.succeed(result.exitCode(), result.output(), null);
    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "Stage %s completed successfully".formatted(job.getStageName()));

    Map<String, Object> definition = resolveDefinitionSnapshot(execution);
    Instant occurredAt = Instant.now();
    if (definition != null) {
      queueNewlyReadyJobs(execution, definition, occurredAt);
    } else {
      log.warn(
          "Missing pipeline definition snapshot on execution; cannot schedule downstream jobs",
          kv("execution_id", execution.getId()));
    }

    finalizeExecutionIfDone(execution);
    recordProcessed(eventId);
    publishExecutionStatusIfChanged(executionStatusBeforeJob, execution, tenantId);

    log.info(
        "Job completed in embedded worker",
        kv("job_id", jobId),
        kv("execution_id", execution.getId()),
        kv("stage_id", job.getStageId()));
  }

  private void queueNewlyReadyJobs(
      ExecutionEntity execution, Map<String, Object> definition, Instant occurredAt) {
    List<JobEntity> jobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execution.getId());
    Set<String> succeeded =
        jobs.stream()
            .filter(j -> j.getStatus() == JobState.SUCCEEDED)
            .map(JobEntity::getStageId)
            .collect(Collectors.toSet());
    Map<String, JobEntity> byStage =
        jobs.stream().collect(Collectors.toMap(JobEntity::getStageId, j -> j, (a, b) -> a));

    for (String stageId : StagePlanner.stagesReadyToQueueAfterSuccesses(definition, succeeded)) {
      JobEntity pending = byStage.get(stageId);
      if (pending == null || pending.getStatus() != JobState.PENDING) {
        continue;
      }
      pending.queue();
      Map<String, Object> jobPayload =
          buildJobCreatedPayload(occurredAt, execution.getTenantId(), execution, pending);
      outboxRepository.save(
          new OutboxEntity(
              AGGREGATE_JOB,
              pending.getId(),
              JobEventTypes.JOB_CREATED,
              jobCreatedTopic,
              execution.getId().toString(),
              jobPayload,
              occurredAt));
    }
  }

  private void finalizeExecutionIfDone(ExecutionEntity execution) {
    if (execution.getStatus() != ExecutionState.RUNNING) {
      return;
    }
    List<JobEntity> jobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execution.getId());

    boolean anyFailed = jobs.stream().anyMatch(j -> j.getStatus() == JobState.FAILED);
    boolean anyActive =
        jobs.stream()
            .anyMatch(
                j ->
                    j.getStatus() == JobState.PENDING
                        || j.getStatus() == JobState.QUEUED
                        || j.getStatus() == JobState.RUNNING);
    if (anyActive) {
      return;
    }
    if (anyFailed) {
      JobEntity failed =
          jobs.stream().filter(j -> j.getStatus() == JobState.FAILED).findFirst().orElseThrow();
      execution.complete(
          false,
          failed.getErrorMessage() != null ? failed.getErrorMessage() : "Job failed",
          "JOB_FAILED");
    } else {
      execution.complete(true, null, null);
    }
  }

  private void recordProcessed(UUID eventId) {
    processedEventRepository.save(
        new ProcessedEventEntity(eventId, JobEventTypes.JOB_CREATED, Instant.now()));
  }

  private static Map<String, Object> buildJobCreatedPayload(
      Instant occurredAt, UUID tenantId, ExecutionEntity execution, JobEntity job) {
    UUID newEventId = UUID.randomUUID();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", newEventId.toString());
    m.put("eventType", JobEventTypes.JOB_CREATED);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", AGGREGATE_JOB);
    m.put("aggregateId", job.getId().toString());
    m.put("tenantId", tenantId.toString());
    m.put("executionId", execution.getId().toString());
    m.put("pipelineId", execution.getPipelineId().toString());
    m.put("pipelineVersion", execution.getPipelineVersion());
    m.put("jobId", job.getId().toString());
    m.put("stageId", job.getStageId());
    m.put("stageName", job.getStageName());
    return m;
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
}
