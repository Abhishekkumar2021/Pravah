package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.StageTimeout;
import io.pravah.common.domain.StageTimeoutParser;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimeEvents;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies timeout failure to a single job in its own transaction (US-02.07). */
@Service
public class JobTimeoutProcessor {

  private static final Logger log = LoggerFactory.getLogger(JobTimeoutProcessor.class);

  private final JobEntityRepository jobEntityRepository;
  private final ExecutionEntityRepository executionEntityRepository;
  private final JobFailureService jobFailureService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ObjectMapper objectMapper;

  public JobTimeoutProcessor(
      JobEntityRepository jobEntityRepository,
      ExecutionEntityRepository executionEntityRepository,
      JobFailureService jobFailureService,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper) {
    this.jobEntityRepository = jobEntityRepository;
    this.executionEntityRepository = executionEntityRepository;
    this.jobFailureService = jobFailureService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processTimedOutJob(UUID jobId, Instant now) {
    JobEntity snapshot = jobEntityRepository.findById(jobId).orElse(null);
    if (snapshot == null || snapshot.getExecutionId() == null) {
      return;
    }
    ExecutionEntity executionSnapshot =
        executionEntityRepository.findById(snapshot.getExecutionId()).orElse(null);
    if (executionSnapshot == null) {
      return;
    }

    UUID tenantId = executionSnapshot.getTenantId();
    TenantContext.setCurrentTenantId(tenantId);
    try {
      processTimedOutJobWithTenant(jobId, now, tenantId);
    } finally {
      TenantContext.clear();
    }
  }

  private void processTimedOutJobWithTenant(UUID jobId, Instant now, UUID tenantId) {
    JobEntity job = jobEntityRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != JobState.RUNNING || job.getStartedAt() == null) {
      return;
    }

    ExecutionEntity execution =
        executionEntityRepository.findById(job.getExecutionId()).orElse(null);
    if (execution == null) {
      return;
    }
    try {
      Map<String, Object> definition = execution.getDefinitionSnapshot();
      StageTimeout timeout = StageTimeoutParser.resolveForStage(definition, job.getStageId());
      if (!timeout.isConfigured() || !timeout.isExpired(job.getStartedAt(), now)) {
        return;
      }

      ExecutionState statusBefore = execution.getStatus();
      String message = "Stage timed out after %d seconds".formatted(timeout.timeoutSeconds());
      boolean handled =
          jobFailureService.handleStageFailure(
              execution, job, tenantId, JobFailureService.EXIT_CODE_TIMEOUT, message, null);
      if (handled) {
        log.info(
            "Job failed due to timeout",
            kv("job_id", job.getId()),
            kv("execution_id", execution.getId()),
            kv("timeout_seconds", timeout.timeoutSeconds()));
        publishExecutionStatusIfChanged(statusBefore, execution, tenantId);
      }
    } catch (Exception e) {
      log.warn("Timeout processing failed", kv("job_id", jobId), kv("error", e.getMessage()));
    }
  }

  void processTimedOutJobSafely(UUID jobId, Instant now) {
    try {
      processTimedOutJob(jobId, now);
    } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
      log.debug(
          "Concurrent job update during timeout check; skipping",
          kv("job_id", jobId),
          kv("error", e.getMessage()));
    }
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
