package io.pravah.execution.api.dto;

import java.util.List;
import java.util.UUID;

public record CreateExecutionResponse(
    UUID id, UUID pipelineId, int pipelineVersion, String status, List<JobResponse> jobs) {

  public record JobResponse(UUID id, String stageId, String stageName, String status) {}
}
