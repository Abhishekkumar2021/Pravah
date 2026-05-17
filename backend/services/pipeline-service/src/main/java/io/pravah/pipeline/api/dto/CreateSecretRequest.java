package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a tenant secret reference.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "name": "api_key",
 *   "description": "External API authentication key",
 *   "provider": "env",
 *   "providerPath": "MY_API_KEY"
 * }
 * }</pre>
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
    @NotBlank @Size(max = 50) String provider,
    @NotBlank @Size(max = 500) String providerPath) {}
