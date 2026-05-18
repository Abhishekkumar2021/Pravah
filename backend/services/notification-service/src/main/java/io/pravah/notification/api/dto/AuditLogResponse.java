package io.pravah.notification.api.dto;

import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID actorId,
    String actorType,
    String actorName,
    String action,
    String resourceType,
    UUID resourceId,
    String resourceName,
    String details,
    String ipAddress,
    String requestId,
    Instant createdAt) {

  public static AuditLogResponse from(AuditLogEntity entity) {
    return new AuditLogResponse(
        entity.getId(),
        entity.getActorId(),
        entity.getActorType() != null ? entity.getActorType().value() : null,
        entity.getActorName(),
        entity.getAction(),
        entity.getResourceType(),
        entity.getResourceId(),
        entity.getResourceName(),
        entity.getDetails(),
        entity.getIpAddress(),
        entity.getRequestId(),
        entity.getCreatedAt());
  }
}
