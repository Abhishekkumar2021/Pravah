package io.pravah.pipeline.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Published pipeline definition for a single immutable version (for execution / runners). */
public record PipelineVersionDefinitionResponse(
    UUID pipelineId,
    int version,
    Map<String, Object> definition,
    Instant publishedAt,
    UUID publishedBy) {}
