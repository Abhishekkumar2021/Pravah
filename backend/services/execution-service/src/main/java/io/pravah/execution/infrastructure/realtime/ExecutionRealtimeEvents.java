package io.pravah.execution.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

public final class ExecutionRealtimeEvents {

  private ExecutionRealtimeEvents() {}

  public static void publishExecutionUpdated(
      ApplicationEventPublisher publisher,
      ObjectMapper objectMapper,
      UUID tenantId,
      UUID executionId,
      String status,
      Instant occurredAt,
      UUID pipelineId) {
    try {
      String json =
          objectMapper.writeValueAsString(
              ExecutionRealtimePayload.executionUpdated(
                  executionId, status, occurredAt, pipelineId));
      publisher.publishEvent(new ExecutionRealtimeNotificationEvent(tenantId, json));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize realtime payload", e);
    }
  }
}
