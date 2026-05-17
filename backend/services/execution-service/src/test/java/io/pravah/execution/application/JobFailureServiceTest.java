package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobFailureServiceTest {

  private static final String JOB_CREATED_TOPIC = "pravah.job.created";

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private JobLogService jobLogService;

  private JobFailureService service;

  @BeforeEach
  void setUp() {
    service =
        new JobFailureService(
            jobEntityRepository, outboxRepository, jobLogService, JOB_CREATED_TOPIC, 3);
  }

  @Test
  void handleStageFailure_timeoutExitCode_schedulesRetry() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID execId = UUID.randomUUID();

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(Map.of("retry", Map.of("max_attempts", 3)))
            .build();
    setId(execution, execId);
    execution.start();

    JobEntity job =
        JobEntity.builder().executionId(execId).stageId("slow").stageName("Slow").build();
    setId(job, UUID.randomUUID());
    job.queue();
    job.assign(EmbeddedRunnerIds.LOCAL);

    boolean handled =
        service.handleStageFailure(
            execution,
            job,
            tenantId,
            JobFailureService.EXIT_CODE_TIMEOUT,
            "Stage timed out after 5 seconds",
            null);

    assertThat(handled).isTrue();
    assertThat(job.getStatus()).isEqualTo(JobState.QUEUED);
    assertThat(job.getExitCode()).isEqualTo(JobFailureService.EXIT_CODE_TIMEOUT);
    verify(outboxRepository).save(any(OutboxEntity.class));
  }

  @Test
  void finalizeExecutionIfDone_marksExecutionFailed() throws Exception {
    UUID execId = UUID.randomUUID();
    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .build();
    setId(execution, execId);
    execution.start();

    JobEntity failed = JobEntity.builder().executionId(execId).stageId("a").stageName("A").build();
    setId(failed, UUID.randomUUID());
    failed.queue();
    failed.assign(EmbeddedRunnerIds.LOCAL);
    failed.fail(1, "boom", false);

    when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execId))
        .thenReturn(List.of(failed));

    service.finalizeExecutionIfDone(execution);

    assertThat(execution.getStatus()).isEqualTo(ExecutionState.FAILED);
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }
}
