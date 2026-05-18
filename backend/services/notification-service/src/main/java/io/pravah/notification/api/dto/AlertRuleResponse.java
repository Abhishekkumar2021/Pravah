package io.pravah.notification.api.dto;

import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import java.time.Instant;
import java.util.UUID;

public record AlertRuleResponse(
    UUID id,
    UUID pipelineId,
    String name,
    String description,
    boolean enabled,
    String conditions,
    String channels,
    int dedupWindowSeconds,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static AlertRuleResponse from(AlertRuleEntity entity) {
    return new AlertRuleResponse(
        entity.getId(),
        entity.getPipelineId(),
        entity.getName(),
        entity.getDescription(),
        entity.isEnabled(),
        entity.getConditions(),
        entity.getChannels(),
        entity.getDedupWindowSeconds(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
