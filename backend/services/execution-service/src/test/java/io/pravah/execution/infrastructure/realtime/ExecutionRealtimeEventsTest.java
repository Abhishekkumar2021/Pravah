package io.pravah.execution.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ExecutionRealtimeEventsTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @Test
  void publishExecutionUpdated_emitsNotificationWithJsonPayload() {
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-05-16T10:00:00Z");

    ExecutionRealtimeEvents.publishExecutionUpdated(
        applicationEventPublisher,
        objectMapper,
        tenantId,
        executionId,
        "running",
        occurredAt,
        pipelineId);

    ArgumentCaptor<ExecutionRealtimeNotificationEvent> captor =
        ArgumentCaptor.forClass(ExecutionRealtimeNotificationEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());

    ExecutionRealtimeNotificationEvent event = captor.getValue();
    assertThat(event.tenantId()).isEqualTo(tenantId);
    assertThat(event.jsonPayload()).contains("\"type\":\"execution.updated\"");
    assertThat(event.jsonPayload()).contains(executionId.toString());
    assertThat(event.jsonPayload()).contains(pipelineId.toString());
  }
}
