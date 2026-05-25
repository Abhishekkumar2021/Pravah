package io.pravah.pipeline.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public secret reference view — never includes the resolved secret value.
 *
 * <p>Only metadata is exposed: name, provider type, path pattern. Actual secret values are resolved
 * at execution time and never stored or returned by the API.
 */
public record SecretResponse(
    UUID id,
    String name,
    String description,
    String provider,
    String providerPath,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {}
