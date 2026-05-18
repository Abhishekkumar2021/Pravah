package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionJobQueueingServiceTest {

  private static final String JOB_CREATED_TOPIC = "pravah.job.created";
  private static final int DEFAULT_MAX_PARALLEL = 4;

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private OutboxRepository outboxRepository;
  @Captor private ArgumentCaptor<OutboxEntity> outboxCaptor;

  private ExecutionJobQueueingService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecutionJobQueueingService(
            jobEntityRepository, outboxRepository, JOB_CREATED_TOPIC, DEFAULT_MAX_PARALLEL);
  }

  @Nested
  class ParallelismEnforcement {

    @Test
    void queuesAllReadyJobsWhenBelowCap() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      ExecutionEntity execution = createExecution(tenantId, executionId, null);

      JobEntity jobA = createJob(executionId, "a", "A", JobState.PENDING);
      JobEntity jobB = createJob(executionId, "b", "B", JobState.PENDING);
      JobEntity jobC = createJob(executionId, "c", "C", JobState.PENDING);

      Map<String, Object> definition =
          Map.of(
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B"),
                  Map.of("id", "c", "name", "C")));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId))
          .thenReturn(List.of(jobA, jobB, jobC));

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(3);
      assertThat(jobA.getStatus()).isEqualTo(JobState.QUEUED);
      assertThat(jobB.getStatus()).isEqualTo(JobState.QUEUED);
      assertThat(jobC.getStatus()).isEqualTo(JobState.QUEUED);
      verify(outboxRepository, times(3)).save(any(OutboxEntity.class));
    }

    @Test
    void respectsDefaultParallelismCap() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      ExecutionEntity execution = createExecution(tenantId, executionId, null);

      List<JobEntity> jobs = new ArrayList<>();
      List<Map<String, Object>> stageDefs = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        String id = "stage-" + i;
        jobs.add(createJob(executionId, id, "Stage " + i, JobState.PENDING));
        stageDefs.add(Map.of("id", id, "name", "Stage " + i));
      }

      Map<String, Object> definition = Map.of("stages", stageDefs);

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(DEFAULT_MAX_PARALLEL);
      long queuedCount = jobs.stream().filter(j -> j.getStatus() == JobState.QUEUED).count();
      assertThat(queuedCount).isEqualTo(DEFAULT_MAX_PARALLEL);
      verify(outboxRepository, times(DEFAULT_MAX_PARALLEL)).save(any(OutboxEntity.class));
    }

    @Test
    void respectsDefinitionOverrideForParallelism() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "execution",
              Map.of("maxParallelStages", 2),
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B"),
                  Map.of("id", "c", "name", "C"),
                  Map.of("id", "d", "name", "D")));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.PENDING),
              createJob(executionId, "b", "B", JobState.PENDING),
              createJob(executionId, "c", "C", JobState.PENDING),
              createJob(executionId, "d", "D", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(2);
      long queuedCount = jobs.stream().filter(j -> j.getStatus() == JobState.QUEUED).count();
      assertThat(queuedCount).isEqualTo(2);
    }

    @Test
    void queuesNothingWhenAtCap() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "execution",
              Map.of("maxParallelStages", 2),
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B"),
                  Map.of("id", "c", "name", "C", "dependsOn", List.of("a", "b"))));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.RUNNING),
              createJob(executionId, "b", "B", JobState.QUEUED),
              createJob(executionId, "c", "C", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isZero();
      assertThat(jobs.get(2).getStatus()).isEqualTo(JobState.PENDING);
      verify(outboxRepository, never()).save(any());
    }

    @Test
    void queuesMoreJobsAsSlotsBecomeAvailable() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "execution",
              Map.of("maxParallelStages", 2),
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B"),
                  Map.of("id", "c", "name", "C")));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.SUCCEEDED),
              createJob(executionId, "b", "B", JobState.RUNNING),
              createJob(executionId, "c", "C", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(1);
      assertThat(jobs.get(2).getStatus()).isEqualTo(JobState.QUEUED);
    }

    @Test
    void countsQueuedAndRunningAsActive() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "execution",
              Map.of("maxParallelStages", 3),
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B"),
                  Map.of("id", "c", "name", "C"),
                  Map.of("id", "d", "name", "D"),
                  Map.of("id", "e", "name", "E")));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.QUEUED),
              createJob(executionId, "b", "B", JobState.RUNNING),
              createJob(executionId, "c", "C", JobState.PENDING),
              createJob(executionId, "d", "D", JobState.PENDING),
              createJob(executionId, "e", "E", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(1);
      long queuedPlusRunning =
          jobs.stream()
              .filter(j -> j.getStatus() == JobState.QUEUED || j.getStatus() == JobState.RUNNING)
              .count();
      assertThat(queuedPlusRunning).isEqualTo(3);
    }
  }

  @Nested
  class DependencyResolution {

    @Test
    void skipsJobsWithUnmetDependencies() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B", "dependsOn", List.of("a")),
                  Map.of("id", "c", "name", "C", "dependsOn", List.of("b"))));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.PENDING),
              createJob(executionId, "b", "B", JobState.PENDING),
              createJob(executionId, "c", "C", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(1);
      assertThat(jobs.get(0).getStatus()).isEqualTo(JobState.QUEUED);
      assertThat(jobs.get(1).getStatus()).isEqualTo(JobState.PENDING);
      assertThat(jobs.get(2).getStatus()).isEqualTo(JobState.PENDING);
    }

    @Test
    void queuesJobsWithSatisfiedDependencies() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B", "dependsOn", List.of("a")),
                  Map.of("id", "c", "name", "C", "dependsOn", List.of("a"))));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.SUCCEEDED),
              createJob(executionId, "b", "B", JobState.PENDING),
              createJob(executionId, "c", "C", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(2);
      assertThat(jobs.get(1).getStatus()).isEqualTo(JobState.QUEUED);
      assertThat(jobs.get(2).getStatus()).isEqualTo(JobState.QUEUED);
    }

    @Test
    void respectsParallelismCapWithDependencies() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      Map<String, Object> definition =
          Map.of(
              "execution",
              Map.of("maxParallelStages", 1),
              "stages",
              List.of(
                  Map.of("id", "a", "name", "A"),
                  Map.of("id", "b", "name", "B", "dependsOn", List.of("a")),
                  Map.of("id", "c", "name", "C", "dependsOn", List.of("a"))));

      ExecutionEntity execution = createExecution(tenantId, executionId, definition);

      List<JobEntity> jobs =
          List.of(
              createJob(executionId, "a", "A", JobState.SUCCEEDED),
              createJob(executionId, "b", "B", JobState.PENDING),
              createJob(executionId, "c", "C", JobState.PENDING));

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId)).thenReturn(jobs);

      int queued = service.queueReadyJobs(execution, definition, Instant.now());

      assertThat(queued).isEqualTo(1);
      long queuedCount = jobs.stream().filter(j -> j.getStatus() == JobState.QUEUED).count();
      assertThat(queuedCount).isEqualTo(1);
    }
  }

  @Nested
  class OutboxPayload {

    @Test
    void includesCorrectEventTypeAndTopic() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      Map<String, Object> definition = Map.of("stages", List.of(Map.of("id", "a", "name", "A")));

      ExecutionEntity execution = createExecution(tenantId, executionId, pipelineId, definition);

      JobEntity job = createJob(executionId, "a", "A", JobState.PENDING);

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId))
          .thenReturn(List.of(job));

      service.queueReadyJobs(execution, definition, Instant.now());

      verify(outboxRepository).save(outboxCaptor.capture());
      OutboxEntity outbox = outboxCaptor.getValue();

      assertThat(outbox.getTopic()).isEqualTo(JOB_CREATED_TOPIC);
      assertThat(outbox.getEventType()).isEqualTo("job.created");
      assertThat(outbox.getAggregateType()).isEqualTo("job");
      assertThat(outbox.getAggregateId()).isEqualTo(job.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void includesRequiredFieldsInPayload() throws Exception {
      UUID tenantId = UUID.randomUUID();
      UUID executionId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      Map<String, Object> definition = Map.of("stages", List.of(Map.of("id", "a", "name", "A")));

      ExecutionEntity execution = createExecution(tenantId, executionId, pipelineId, definition);

      JobEntity job = createJob(executionId, "a", "Stage A", JobState.PENDING);

      when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId))
          .thenReturn(List.of(job));

      service.queueReadyJobs(execution, definition, Instant.now());

      verify(outboxRepository).save(outboxCaptor.capture());
      OutboxEntity outbox = outboxCaptor.getValue();
      Map<String, Object> payload = outbox.getPayload();

      assertThat(payload)
          .containsKey("eventId")
          .containsEntry("eventType", "job.created")
          .containsEntry("aggregateType", "job")
          .containsEntry("aggregateId", job.getId().toString())
          .containsEntry("tenantId", tenantId.toString())
          .containsEntry("executionId", executionId.toString())
          .containsEntry("pipelineId", pipelineId.toString())
          .containsEntry("jobId", job.getId().toString())
          .containsEntry("stageId", "a")
          .containsEntry("stageName", "Stage A");
    }
  }

  private ExecutionEntity createExecution(
      UUID tenantId, UUID executionId, Map<String, Object> definition) throws Exception {
    return createExecution(tenantId, executionId, UUID.randomUUID(), definition);
  }

  private ExecutionEntity createExecution(
      UUID tenantId, UUID executionId, UUID pipelineId, Map<String, Object> definition)
      throws Exception {
    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(pipelineId)
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(definition)
            .build();
    setId(execution, executionId);
    setStatus(execution, ExecutionState.RUNNING);
    return execution;
  }

  private JobEntity createJob(UUID executionId, String stageId, String stageName, JobState status)
      throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(executionId)
            .stageId(stageId)
            .stageName(stageName)
            .status(status)
            .build();
    setId(job, UUID.randomUUID());
    return job;
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }

  private static void setStatus(ExecutionEntity execution, ExecutionState status) throws Exception {
    Field f = ExecutionEntity.class.getDeclaredField("status");
    f.setAccessible(true);
    f.set(execution, status);
  }
}
