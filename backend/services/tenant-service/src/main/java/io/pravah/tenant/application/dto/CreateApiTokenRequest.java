package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request to create a new API token (US-10.08). */
public record CreateApiTokenRequest(
    @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,
    @NotEmpty(message = "At least one permission is required") List<@NotBlank String> permissions,
    @Future(message = "Expiration must be in the future") Instant expiresAt) {}
