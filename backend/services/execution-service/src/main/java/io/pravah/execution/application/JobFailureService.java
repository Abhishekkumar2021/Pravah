package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.RetryPolicy;
import io.pravah.common.domain.RetryPolicyParser;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared job failure handling with optional auto-retry (US-02.06) and timeout (US-02.07). */
@Service
public class JobFailureService {

  public static final int EXIT_CODE_TIMEOUT = 124;

  private static final Logger log = LoggerFactory.getLogger(JobFailureService.class);
  private static final String AGGREGATE_JOB = "job";
  private static final String AGGREGATE_EXECUTION = "execution";

  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final JobLogService jobLogService;
  private final CheckpointService checkpointService;
  private final String jobCreatedTopic;
  private final String executionEventsTopic;
  private final int maxJobAttempts;

  public JobFailureService(
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      JobLogService jobLogService,
      CheckpointService checkpointService,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic,
      @Value("${pravah.outbox.topic.execution-events}") String executionEventsTopic,
      @Value("${pravah.job.max-attempts:3}") int maxJobAttempts) {
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.jobLogService = jobLogService;
    this.checkpointService = checkpointService;
    this.jobCreatedTopic = jobCreatedTopic;
    this.executionEventsTopic = executionEventsTopic;
    this.maxJobAttempts = maxJobAttempts;
  }

  /**
   * Records stage failure, schedules retry when policy allows, and finalizes execution when no jobs
   * remain active.
   *
   * @return true when the job was in {@link JobState#RUNNING} and failure handling ran
   */
  @Transactional
  public boolean handleStageFailure(
      ExecutionEntity execution,
      JobEntity job,
      UUID tenantId,
      int exitCode,
      String errorMessage,
      Map<String, Object> output) {
    if (job.getStatus() != JobState.RUNNING) {
      return false;
    }

    Map<String, Object> definition = execution.getDefinitionSnapshot();
    RetryPolicy retryPolicy = resolveRetryPolicy(definition, job.getStageId());
    Instant now = Instant.now();
    Instant retryWindowStart =
        job.getQueuedAt() != null ? job.getQueuedAt() : execution.getCreatedAt();
    boolean scheduleRetry =
        retryPolicy.shouldScheduleRetry(job.getAttempt(), exitCode, retryWindowStart, now);

    job.fail(exitCode, errorMessage, output, scheduleRetry);
    jobLogService.append(job.getId(), JobLogLevel.ERROR, errorMessage);

    if (job.getStatus() == JobState.QUEUED) {
      Duration delay = retryPolicy.delayBeforeAttempt(job.getAttempt());
      Instant retryPublishAt = now.plus(delay);
      jobLogService.append(
          job.getId(),
          JobLogLevel.WARN,
          "Scheduling retry (attempt %d of %d)%s"
              .formatted(
                  job.getAttempt(),
                  retryPolicy.maxAttempts(),
                  delay.isZero() ? "" : " after %ds".formatted(delay.getSeconds())));
      Map<String, Object> retryPayload =
          buildJobCreatedPayload(retryPublishAt, tenantId, execution, job);
      outboxRepository.save(
          new OutboxEntity(
              AGGREGATE_JOB,
              job.getId(),
              JobEventTypes.JOB_CREATED,
              jobCreatedTopic,
              execution.getId().toString(),
              retryPayload,
              retryPublishAt));
      log.info(
          "Scheduled job retry",
          kv("job_id", job.getId()),
          kv("attempt", job.getAttempt()),
          kv("max_attempts", retryPolicy.maxAttempts()),
          kv("delay_seconds", delay.getSeconds()),
          kv("scheduled_at", retryPublishAt.toString()));
    } else if (job.getStatus() == JobState.FAILED) {
      jobLogService.append(
          job.getId(),
          JobLogLevel.ERROR,
          "No further retries (max attempts %d reached or exit code not retryable)"
              .formatted(retryPolicy.maxAttempts()));
    }

    finalizeExecutionIfDone(execution);
    return true;
  }

  public void finalizeExecutionIfDone(ExecutionEntity execution) {
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
    UUID tenantId = execution.getTenantId();
    if (anyFailed) {
      JobEntity failed =
          jobs.stream().filter(j -> j.getStatus() == JobState.FAILED).findFirst().orElseThrow();
      execution.complete(
          false,
          failed.getErrorMessage() != null ? failed.getErrorMessage() : "Job failed",
          "JOB_FAILED");
      publishExecutionTerminalEvent(execution, tenantId, ExecutionEventTypes.EXECUTION_FAILED);
    } else {
      execution.complete(true, null, null);
      checkpointService.clearForExecution(execution.getId());
      publishExecutionTerminalEvent(execution, tenantId, ExecutionEventTypes.EXECUTION_COMPLETED);
    }
  }

  private void publishExecutionTerminalEvent(
      ExecutionEntity execution, UUID tenantId, String eventType) {
    Instant now = Instant.now();
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload =
        buildExecutionTerminalPayload(eventId, now, tenantId, execution, eventType);
    outboxRepository.save(
        new OutboxEntity(
            AGGREGATE_EXECUTION,
            execution.getId(),
            eventType,
            executionEventsTopic,
            execution.getId().toString(),
            payload,
            now));
    log.info(
        "Queued execution terminal event",
        kv("execution_id", execution.getId()),
        kv("event_type", eventType),
        kv("status", execution.getStatus().asDatabaseValue()));
  }

  private static Map<String, Object> buildExecutionTerminalPayload(
      UUID eventId,
      Instant occurredAt,
      UUID tenantId,
      ExecutionEntity execution,
      String eventType) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", eventId.toString());
    m.put("eventType", eventType);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", AGGREGATE_EXECUTION);
    m.put("aggregateId", execution.getId().toString());
    m.put("tenantId", tenantId.toString());
    m.put("executionId", execution.getId().toString());
    m.put("pipelineId", execution.getPipelineId().toString());
    m.put("pipelineVersion", execution.getPipelineVersion());
    m.put("status", execution.getStatus().asDatabaseValue());
    String pipelineName = pipelineNameFromSnapshot(execution.getDefinitionSnapshot());
    if (pipelineName != null) {
      m.put("pipelineName", pipelineName);
    }
    if (execution.getErrorMessage() != null) {
      m.put("errorMessage", execution.getErrorMessage());
    }
    if (execution.getErrorCategory() != null) {
      m.put("errorCategory", execution.getErrorCategory());
    }
    return m;
  }

  private static String pipelineNameFromSnapshot(Map<String, Object> definitionSnapshot) {
    if (definitionSnapshot == null) {
      return null;
    }
    Object name = definitionSnapshot.get("name");
    return name != null ? name.toString() : null;
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

  private RetryPolicy resolveRetryPolicy(Map<String, Object> definition, String stageId) {
    if (definition == null || definition.isEmpty()) {
      return new RetryPolicy(maxJobAttempts, 0, 1.0, java.util.Set.of(), null);
    }
    return RetryPolicyParser.resolveForStage(definition, stageId);
  }
}
