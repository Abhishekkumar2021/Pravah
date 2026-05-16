package io.pravah.execution.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class JobTimeoutProcessorTest {

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private JobFailureService jobFailureService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private JobTimeoutProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new JobTimeoutProcessor(
            jobEntityRepository,
            executionEntityRepository,
            jobFailureService,
            applicationEventPublisher,
            new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  @Test
  void processTimedOutJob_expiredRunningJob_invokesFailureService() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID execId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    JobEntity job =
        JobEntity.builder().executionId(execId).stageId("slow").stageName("Slow").build();
    setId(job, jobId);
    job.queue();
    job.assign(EmbeddedRunnerIds.LOCAL);
    setStartedAt(job, Instant.now().minusSeconds(10));

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(Map.of("timeout_seconds", 5))
            .build();
    setId(execution, execId);
    execution.start();

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(execId)).thenReturn(Optional.of(execution));
    when(jobFailureService.handleStageFailure(any(), any(), eq(tenantId), eq(124), any()))
        .thenReturn(true);

    processor.processTimedOutJob(jobId, Instant.now());

    verify(jobFailureService)
        .handleStageFailure(
            any(),
            eq(job),
            eq(tenantId),
            eq(JobFailureService.EXIT_CODE_TIMEOUT),
            eq("Stage timed out after 5 seconds"));
  }

  @Test
  void processTimedOutJob_withinLimit_skips() throws Exception {
    UUID execId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    JobEntity job =
        JobEntity.builder().executionId(execId).stageId("slow").stageName("Slow").build();
    setId(job, jobId);
    job.queue();
    job.assign(EmbeddedRunnerIds.LOCAL);

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(Map.of("timeout_seconds", 300))
            .build();
    setId(execution, execId);

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(execId)).thenReturn(Optional.of(execution));

    processor.processTimedOutJob(jobId, Instant.now());

    verify(jobFailureService, never())
        .handleStageFailure(any(), any(), any(), any(Integer.class), any());
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }

  private static void setStartedAt(JobEntity job, Instant startedAt) throws Exception {
    Field f = JobEntity.class.getDeclaredField("startedAt");
    f.setAccessible(true);
    f.set(job, startedAt);
  }
}
