package io.pravah.pipeline.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineDetailResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    int currentVersion,
    String status,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    List<VersionSummary> versions) {

  public record VersionSummary(int version, Instant publishedAt, UUID publishedBy) {}
}
