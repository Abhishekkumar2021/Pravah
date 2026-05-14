package io.pravah.execution.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetExecutionResponse(
    UUID id,
    UUID pipelineId,
    int pipelineVersion,
    String status,
    String triggerType,
    UUID triggeredBy,
    Instant createdAt,
    List<JobSummary> jobs) {

  public record JobSummary(UUID id, String stageId, String stageName, String status, int attempt) {}
}
