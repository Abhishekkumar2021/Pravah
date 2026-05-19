package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
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
class RemoteJobCompletionServiceTest {

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private JobLogService jobLogService;
  @Mock private JobFailureService jobFailureService;
  @Mock private CheckpointService checkpointService;
  @Mock private ExecutionJobQueueingService executionJobQueueingService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private RemoteJobCompletionService service;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();
  private final UUID executionId = UUID.randomUUID();
  private final UUID runnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new RemoteJobCompletionService(
            jobEntityRepository,
            executionEntityRepository,
            jobLogService,
            jobFailureService,
            checkpointService,
            executionJobQueueingService,
            applicationEventPublisher,
            new ObjectMapper(),
            1_048_576L,
            102_400L);
  }

  @Test
  void completesRunningJob() {
    JobEntity job =
        JobEntity.builder().executionId(executionId).stageId("s1").stageName("S1").build();
    setField(job, "id", jobId);
    job.queue();
    job.assign(runnerId);

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType(ExecutionApplicationService.TRIGGER_MANUAL)
            .definitionSnapshot(Map.of("stages", java.util.List.of()))
            .build();
    setField(execution, "id", executionId);
    execution.start();

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));

    service.completeJob(tenantId, jobId, runnerId, 0, Map.of("ok", true));

    assertThat(job.getStatus()).isEqualTo(JobState.SUCCEEDED);
    verify(checkpointService).saveAfterJobSuccess(job);
  }

  @Test
  void assignsQueuedJobBeforeCompletion() {
    JobEntity job =
        JobEntity.builder().executionId(executionId).stageId("s1").stageName("S1").build();
    setField(job, "id", jobId);
    job.queue();

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType(ExecutionApplicationService.TRIGGER_MANUAL)
            .build();
    setField(execution, "id", executionId);
    execution.start();

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));

    service.completeJob(tenantId, jobId, runnerId, 0, Map.of());

    assertThat(job.getStatus()).isEqualTo(JobState.SUCCEEDED);
    assertThat(job.getRunnerId()).isEqualTo(runnerId);
  }

  @Test
  void ignoresDuplicateTerminalCompletion() {
    JobEntity job =
        JobEntity.builder().executionId(executionId).stageId("s1").stageName("S1").build();
    setField(job, "id", jobId);
    job.queue();
    job.assign(runnerId);
    job.succeed(0, Map.of(), null);

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));

    service.completeJob(tenantId, jobId, runnerId, 0, Map.of());

    verify(jobFailureService, never())
        .handleStageFailure(any(), any(), any(), anyInt(), any(), any());
    verify(checkpointService, never()).saveAfterJobSuccess(any());
  }

  private static void setField(Object target, String field, Object value) {
    try {
      var f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
