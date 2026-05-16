package io.pravah.tenant.application.dto;

import io.pravah.tenant.application.security.RolePermissions;
import io.pravah.tenant.domain.model.ApiToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API token metadata (never includes the secret value). */
public record ApiTokenResponse(
    UUID id,
    UUID userId,
    String name,
    List<String> permissions,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant createdAt,
    Instant revokedAt) {

  public static ApiTokenResponse from(ApiToken token) {
    return new ApiTokenResponse(
        token.getId(),
        token.getUserId(),
        token.getName(),
        RolePermissions.parse(token.getPermissions()),
        token.getExpiresAt(),
        token.getLastUsedAt(),
        token.getCreatedAt(),
        token.getRevokedAt());
  }
}
