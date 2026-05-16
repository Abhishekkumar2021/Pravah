package io.pravah.execution.infrastructure.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Ephemeral execution update pushed to browsers (WebSocket). Durable truth remains Kafka/outbox.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionRealtimePayload(
    @JsonProperty("type") String type,
    @JsonProperty("executionId") UUID executionId,
    @JsonProperty("status") String status,
    @JsonProperty("occurredAt") Instant occurredAt,
    @JsonProperty("pipelineId") UUID pipelineId) {

  public static final String TYPE_EXECUTION_UPDATED = "execution.updated";

  public static ExecutionRealtimePayload executionUpdated(
      UUID executionId, String status, Instant occurredAt, UUID pipelineId) {
    return new ExecutionRealtimePayload(
        TYPE_EXECUTION_UPDATED, executionId, status, occurredAt, pipelineId);
  }
}
