package io.pravah.pipeline.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Public connection view — never includes resolved passwords.
 *
 * <p>The {@code config} contains connection settings including credential references (e.g., {@code
 * credentials.password: "env:VAR"}), but never exposes the actual resolved secret values.
 */
public record ConnectionResponse(
    UUID id,
    String name,
    String type,
    Map<String, Object> config,
    UUID createdBy,
    Instant createdAt) {}
