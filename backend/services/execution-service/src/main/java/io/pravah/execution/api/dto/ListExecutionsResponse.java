package io.pravah.execution.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListExecutionsResponse(
    List<ExecutionListItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {

  public record ExecutionListItem(
      UUID id,
      UUID pipelineId,
      int pipelineVersion,
      String status,
      String triggerType,
      UUID triggeredBy,
      Instant createdAt,
      Instant startedAt,
      Instant completedAt) {}
}
