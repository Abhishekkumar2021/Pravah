package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.StageOutputSizeEnforcement;
import io.pravah.common.domain.StageOutputSizeGuard;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimeEvents;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes jobs that ran on external runners (runner-service callback). */
@Service
public class RemoteJobCompletionService {

  private static final Logger log = LoggerFactory.getLogger(RemoteJobCompletionService.class);

  private final JobEntityRepository jobEntityRepository;
  private final ExecutionEntityRepository executionEntityRepository;
  private final JobLogService jobLogService;
  private final JobFailureService jobFailureService;
  private final CheckpointService checkpointService;
  private final ExecutionJobQueueingService executionJobQueueingService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ObjectMapper objectMapper;
  private final long stageOutputMaxBytes;
  private final long stageOutputWarnBytes;

  public RemoteJobCompletionService(
      JobEntityRepository jobEntityRepository,
      ExecutionEntityRepository executionEntityRepository,
      JobLogService jobLogService,
      JobFailureService jobFailureService,
      CheckpointService checkpointService,
      ExecutionJobQueueingService executionJobQueueingService,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper,
      @Value("${pravah.stage.max-output-bytes:1048576}") long stageOutputMaxBytes,
      @Value("${pravah.stage.output-warn-bytes:102400}") long stageOutputWarnBytes) {
    this.jobEntityRepository = jobEntityRepository;
    this.executionEntityRepository = executionEntityRepository;
    this.jobLogService = jobLogService;
    this.jobFailureService = jobFailureService;
    this.checkpointService = checkpointService;
    this.executionJobQueueingService = executionJobQueueingService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
    this.stageOutputMaxBytes = stageOutputMaxBytes;
    this.stageOutputWarnBytes = stageOutputWarnBytes;
  }

  @Transactional
  public void completeJob(
      UUID tenantId, UUID jobId, UUID runnerId, int exitCode, Map<String, Object> output) {
    JobEntity job =
        jobEntityRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

    JobState jobStatus = job.getStatus();
    if (jobStatus == JobState.SUCCEEDED || jobStatus == JobState.FAILED) {
      log.debug(
          "Ignoring duplicate remote completion", kv("job_id", jobId), kv("status", jobStatus));
      return;
    }
    if (jobStatus == JobState.QUEUED && runnerId != null) {
      job.assign(runnerId);
      jobStatus = job.getStatus();
    }
    if (job.getRunnerId() != null && runnerId != null && !job.getRunnerId().equals(runnerId)) {
      log.warn(
          "Ignoring remote completion from unexpected runner",
          kv("job_id", jobId),
          kv("expected_runner_id", job.getRunnerId()),
          kv("actual_runner_id", runnerId));
      return;
    }
    if (jobStatus != JobState.RUNNING) {
      log.warn(
          "Ignoring remote completion for job in unexpected state",
          kv("job_id", jobId),
          kv("status", jobStatus));
      return;
    }

    ExecutionEntity execution =
        executionEntityRepository
            .findById(job.getExecutionId())
            .orElseThrow(() -> new IllegalStateException("Execution not found"));
    if (!execution.getTenantId().equals(tenantId)) {
      throw new IllegalStateException("Execution tenant mismatch");
    }
    ExecutionState statusBefore = execution.getStatus();

    if (exitCode != 0) {
      String errorMessage =
          "Remote stage %s failed with exit code %d".formatted(job.getStageName(), exitCode);
      jobFailureService.handleStageFailure(
          execution,
          job,
          tenantId,
          exitCode,
          errorMessage,
          enforceStageOutput(output, jobId, job.getStageId()));
    } else {
      job.succeed(exitCode, enforceStageOutput(output, jobId, job.getStageId()), null);
      checkpointService.saveAfterJobSuccess(job);
      jobLogService.append(
          job.getId(),
          JobLogLevel.INFO,
          "Remote stage %s completed successfully".formatted(job.getStageName()));
      Map<String, Object> definition = execution.getDefinitionSnapshot();
      if (definition != null) {
        executionJobQueueingService.queueReadyJobs(execution, definition, Instant.now());
      }
    }

    jobFailureService.finalizeExecutionIfDone(execution);
    if (execution.getStatus() != statusBefore) {
      ExecutionRealtimeEvents.publishExecutionUpdated(
          applicationEventPublisher,
          objectMapper,
          tenantId,
          execution.getId(),
          execution.getStatus().asDatabaseValue(),
          Instant.now(),
          execution.getPipelineId());
    }
    log.info("Remote job completed", kv("job_id", jobId), kv("exit_code", exitCode));
  }

  private Map<String, Object> enforceStageOutput(
      Map<String, Object> output, UUID jobId, String stageId) {
    StageOutputSizeEnforcement enforced =
        StageOutputSizeGuard.enforce(
            output, stageOutputMaxBytes, stageOutputWarnBytes, objectMapper);
    return enforced.output();
  }
}
