package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a tenant secret reference or store an encrypted value.
 *
 * <p>Pointer mode (legacy): {@code provider} + {@code providerPath} without {@code value}.
 *
 * <p>Stored mode (ADR-007 Transit): {@code value} encrypts via Vault Transit; defaults to {@code
 * provider=transit}. Values are never returned in API responses.
 */
public record CreateSecretRequest(
    @NotBlank
        @Size(max = 255)
        @Pattern(
            regexp = "^[a-z_][a-z0-9_]*$",
            message =
                "name must be lowercase alphanumeric with underscores, starting with letter or underscore")
        String name,
    @Size(max = 500) String description,
    @Size(max = 50) String provider,
    @Size(max = 500) String providerPath,
    @Size(max = 65536) String value) {}
