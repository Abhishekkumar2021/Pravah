package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.exception.AccessDeniedException;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.pipeline.InternalHttpPipelineCatalog;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
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
class ExecutionApplicationServiceCancelTest {

  private static final String EXECUTION_EVENTS_TOPIC = "pravah.execution.execution.events";

  @Mock private PipelineCatalog pipelineCatalog;
  @Mock private InternalHttpPipelineCatalog internalPipelineCatalog;
  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private EntityManager entityManager;
  @Mock private CheckpointService checkpointService;
  @Mock private ExecutionJobQueueingService executionJobQueueingService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ExecutionApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecutionApplicationService(
            pipelineCatalog,
            internalPipelineCatalog,
            executionEntityRepository,
            jobEntityRepository,
            outboxRepository,
            entityManager,
            checkpointService,
            executionJobQueueingService,
            EXECUTION_EVENTS_TOPIC,
            applicationEventPublisher,
            objectMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void cancelExecution_triggeredByMismatch_throwsAccessDenied() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(otherUserId);

    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType(ExecutionApplicationService.TRIGGER_MANUAL)
            .triggeredBy(ownerId)
            .build();
    setId(execution, executionId);

    when(executionEntityRepository.findById(executionId)).thenReturn(Optional.of(execution));

    assertThatThrownBy(() -> service.cancelExecution(executionId))
        .isInstanceOf(AccessDeniedException.class);

    verify(outboxRepository, never()).save(any());
  }

  private static void setId(ExecutionEntity entity, UUID id) throws Exception {
    Field f = ExecutionEntity.class.getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }
}
