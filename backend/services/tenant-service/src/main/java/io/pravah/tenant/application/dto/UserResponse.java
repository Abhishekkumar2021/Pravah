package io.pravah.tenant.application.dto;

import io.pravah.tenant.domain.model.User;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for user data. */
public record UserResponse(
    UUID id,
    UUID tenantId,
    String email,
    String name,
    User.Status status,
    UserRoleSummary role,
    Instant lastLoginAt,
    Instant createdAt) {

  public static UserResponse from(User user, UserRoleSummary role) {
    return new UserResponse(
        user.getId(),
        user.getTenantId(),
        user.getEmail(),
        user.getName(),
        user.getStatus(),
        role,
        user.getLastLoginAt(),
        user.getCreatedAt());
  }
}
