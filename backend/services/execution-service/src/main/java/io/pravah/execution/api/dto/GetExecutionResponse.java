package io.pravah.execution.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GetExecutionResponse(
    UUID id,
    UUID pipelineId,
    int pipelineVersion,
    String status,
    String triggerType,
    UUID triggeredBy,
    UUID retryOf,
    int retryCount,
    Instant createdAt,
    List<JobSummary> jobs) {

  /**
   * Job summary with timing fields for Gantt chart visualization (US-02.09).
   *
   * <p>Timing fields:
   *
   * <ul>
   *   <li>{@code queuedAt}: when the job was queued (dependencies satisfied, waiting for worker)
   *   <li>{@code startedAt}: when execution began (worker picked up the job)
   *   <li>{@code completedAt}: when execution finished (success, failure, or cancellation)
   * </ul>
   */
  public record JobSummary(
      UUID id,
      String stageId,
      String stageName,
      String status,
      int attempt,
      int maxAttempts,
      Instant queuedAt,
      Instant startedAt,
      Instant completedAt,
      Map<String, Object> output) {}
}
