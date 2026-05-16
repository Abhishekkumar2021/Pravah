package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import io.pravah.execution.infrastructure.persistence.entity.ProcessedEventEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.persistence.repository.ProcessedEventRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ExecutionCreatedProcessingServiceTest {

  private static final String JOB_CREATED_TOPIC = "pravah.job.created";

  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private ExecutionCreatedProcessingService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecutionCreatedProcessingService(
            executionEntityRepository,
            jobEntityRepository,
            outboxRepository,
            processedEventRepository,
            JOB_CREATED_TOPIC,
            applicationEventPublisher,
            objectMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void process_queuesPendingRootJobsAndStartsExecution() {
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);

    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getTenantId()).thenReturn(tenantId);
    when(execution.getStatus()).thenReturn(ExecutionState.PENDING);
    when(execution.getId()).thenReturn(executionId);
    when(execution.getPipelineId()).thenReturn(pipelineId);
    when(execution.getPipelineVersion()).thenReturn(3);

    JobEntity job = mock(JobEntity.class);
    when(job.getId()).thenReturn(jobId);
    when(job.getStageId()).thenReturn("extract");
    when(job.getStageName()).thenReturn("Extract");
    when(job.getStatus()).thenReturn(JobState.PENDING);

    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));
    when(jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId))
        .thenReturn(List.of(job));

    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "tenantId", tenantId.toString(),
            "executionId", executionId.toString(),
            "rootStageIds", List.of("extract"));

    service.processExecutionCreated(payload);

    verify(job).queue();
    verify(execution).start();

    ArgumentCaptor<OutboxEntity> outboxCap = ArgumentCaptor.forClass(OutboxEntity.class);
    verify(outboxRepository).save(outboxCap.capture());
    assertThat(outboxCap.getValue().getEventType()).isEqualTo(JobEventTypes.JOB_CREATED);
    assertThat(outboxCap.getValue().getTopic()).isEqualTo(JOB_CREATED_TOPIC);

    ArgumentCaptor<ProcessedEventEntity> procCap =
        ArgumentCaptor.forClass(ProcessedEventEntity.class);
    verify(processedEventRepository).save(procCap.capture());
    assertThat(procCap.getValue().getEventId()).isEqualTo(eventId);
  }

  @Test
  void process_idempotent_whenEventAlreadyProcessed() {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "tenantId", tenantId.toString(),
            "executionId", executionId.toString(),
            "rootStageIds", List.of("extract"));

    service.processExecutionCreated(payload);

    verify(executionEntityRepository, never()).findById(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void process_skipsWhenExecutionNotPending() {
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getTenantId()).thenReturn(tenantId);
    when(execution.getStatus()).thenReturn(ExecutionState.CANCELLED);

    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));

    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "tenantId", tenantId.toString(),
            "executionId", executionId.toString(),
            "rootStageIds", List.of("extract"));

    service.processExecutionCreated(payload);

    verify(jobEntityRepository, never()).findByExecutionIdOrderByStageIdAsc(any());
    verify(outboxRepository, never()).save(any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void process_duplicateEventWhenExecutionAlreadyRunning_skipsWithoutJobOutbox() {
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getTenantId()).thenReturn(tenantId);
    when(execution.getStatus()).thenReturn(ExecutionState.RUNNING);

    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));

    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "tenantId", tenantId.toString(),
            "executionId", executionId.toString(),
            "rootStageIds", List.of("extract"));

    service.processExecutionCreated(payload);

    verify(jobEntityRepository, never()).findByExecutionIdOrderByStageIdAsc(any());
    verify(outboxRepository, never()).save(any());
    verify(processedEventRepository).save(any());
  }
}
