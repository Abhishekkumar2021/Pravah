package io.pravah.pipeline.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PipelineResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    int currentVersion,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
