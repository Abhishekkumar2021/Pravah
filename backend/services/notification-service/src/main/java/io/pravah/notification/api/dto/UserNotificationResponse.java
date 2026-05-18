package io.pravah.notification.api.dto;

import io.pravah.notification.infrastructure.persistence.entity.UserNotificationEntity;
import java.time.Instant;
import java.util.UUID;

public record UserNotificationResponse(
    UUID id,
    String title,
    String message,
    String type,
    String resourceType,
    UUID resourceId,
    String linkUrl,
    boolean read,
    Instant readAt,
    Instant createdAt) {

  public static UserNotificationResponse from(UserNotificationEntity entity) {
    return new UserNotificationResponse(
        entity.getId(),
        entity.getTitle(),
        entity.getMessage(),
        entity.getType() != null ? entity.getType().value() : null,
        entity.getResourceType(),
        entity.getResourceId(),
        entity.getLinkUrl(),
        entity.isRead(),
        entity.getReadAt(),
        entity.getCreatedAt());
  }
}
