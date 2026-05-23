package io.pravah.scheduler.api.dto;

import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchHistoryEntity;
import java.time.Instant;
import java.util.UUID;

public record TriggerDispatchHistoryResponse(
    UUID id,
    UUID triggerId,
    String triggerType,
    String status,
    UUID executionId,
    String errorMessage,
    Instant createdAt) {

  public static TriggerDispatchHistoryResponse from(TriggerDispatchHistoryEntity entity) {
    return new TriggerDispatchHistoryResponse(
        entity.getId(),
        entity.getTriggerId(),
        entity.getTriggerType(),
        entity.getStatus(),
        entity.getExecutionId(),
        entity.getErrorMessage(),
        entity.getCreatedAt());
  }
}
