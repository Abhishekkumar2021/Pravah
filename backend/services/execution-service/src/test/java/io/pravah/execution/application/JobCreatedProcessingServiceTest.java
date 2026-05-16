package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.persistence.repository.ProcessedEventRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class JobCreatedProcessingServiceTest {

  private static final String JOB_CREATED_TOPIC = "pravah.job.created";

  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private EmbeddedStageExecutor embeddedStageExecutor;
  @Mock private JobLogService jobLogService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private JobCreatedProcessingService service;

  @BeforeEach
  void setUp() {
    service =
        new JobCreatedProcessingService(
            executionEntityRepository,
            jobEntityRepository,
            outboxRepository,
            processedEventRepository,
            embeddedStageExecutor,
            jobLogService,
            JOB_CREATED_TOPIC,
            3,
            applicationEventPublisher,
            objectMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void process_duplicateEvent_skips() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

    service.processJobCreated(Map.of("eventId", eventId.toString()));

    verify(embeddedStageExecutor, never()).execute(any(), any());
  }

  @Test
  void process_notQueued_acknowledgesWithoutExecutor() {
    UUID tenantId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID execId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    JobEntity job = JobEntity.builder().executionId(execId).stageId("a").stageName("A").build();
    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));

    service.processJobCreated(
        Map.of(
            "eventId",
            eventId.toString(),
            "tenantId",
            tenantId.toString(),
            "jobId",
            jobId.toString(),
            "executionId",
            execId.toString()));

    verify(embeddedStageExecutor, never()).execute(any(), any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void process_success_completesSingleJobExecution() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID execId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    Map<String, Object> definition = Map.of("stages", List.of(Map.of("id", "a", "name", "A")));

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(pipelineId)
            .pipelineVersion(1)
            .triggerType(ExecutionApplicationService.TRIGGER_MANUAL)
            .definitionSnapshot(definition)
            .build();
    setId(execution, execId);
    execution.start();

    JobEntity job = JobEntity.builder().executionId(execId).stageId("a").stageName("A").build();
    setId(job, jobId);
    job.queue();

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(execId)).thenReturn(Optional.of(execution));
    when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execId)).thenReturn(List.of(job));

    when(embeddedStageExecutor.execute(job, execution))
        .thenReturn(new EmbeddedStageExecutor.StageExecutionResult(0, Map.of("ok", true)));

    service.processJobCreated(
        Map.of(
            "eventId",
            eventId.toString(),
            "tenantId",
            tenantId.toString(),
            "jobId",
            jobId.toString(),
            "executionId",
            execId.toString()));

    assertThat(job.getStatus()).isEqualTo(JobState.SUCCEEDED);
    assertThat(execution.getStatus()).isEqualTo(ExecutionState.SUCCEEDED);
    verify(processedEventRepository).save(any());
  }

  @Test
  void process_failureWithRetry_writesJobCreatedOutboxEvent() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID execId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(pipelineId)
            .pipelineVersion(1)
            .triggerType(ExecutionApplicationService.TRIGGER_MANUAL)
            .build();
    setId(execution, execId);
    execution.start();

    JobEntity job = JobEntity.builder().executionId(execId).stageId("a").stageName("A").build();
    setId(job, jobId);
    job.queue();

    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(executionEntityRepository.findById(execId)).thenReturn(Optional.of(execution));
    when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execId)).thenReturn(List.of(job));
    when(embeddedStageExecutor.execute(job, execution))
        .thenReturn(new EmbeddedStageExecutor.StageExecutionResult(1, Map.of()));

    service.processJobCreated(
        Map.of(
            "eventId",
            eventId.toString(),
            "tenantId",
            tenantId.toString(),
            "jobId",
            jobId.toString(),
            "executionId",
            execId.toString()));

    assertThat(job.getStatus()).isEqualTo(JobState.QUEUED);
    verify(outboxRepository)
        .save(
            argThat(
                (OutboxEntity row) ->
                    JOB_CREATED_TOPIC.equals(row.getTopic())
                        && JobEventTypes.JOB_CREATED.equals(row.getEventType())
                        && jobId.equals(row.getAggregateId())));
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }
}
