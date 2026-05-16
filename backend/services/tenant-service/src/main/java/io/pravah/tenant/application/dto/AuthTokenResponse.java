package io.pravah.tenant.application.dto;

import java.time.Instant;
import java.util.UUID;

/** JWT issued after successful login or registration. */
public record AuthTokenResponse(
    String accessToken, UUID userId, UUID tenantId, Instant expiresAt, UserResponse user) {}
