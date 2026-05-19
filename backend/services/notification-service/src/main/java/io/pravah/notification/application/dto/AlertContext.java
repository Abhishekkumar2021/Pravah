package io.pravah.notification.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Context for an alert notification, containing all data needed for rendering and delivery. */
public record AlertContext(
    UUID tenantId,
    UUID executionId,
    UUID pipelineId,
    String pipelineName,
    String eventType,
    String status,
    String errorMessage,
    String stageName,
    Instant occurredAt,
    String environment,
    Map<String, Object> metadata,
    String uiBaseUrl) {

  /** Generate a URL to the execution detail page in the UI. */
  public String executionUrl() {
    if (uiBaseUrl == null || executionId == null) {
      return null;
    }
    return uiBaseUrl + "/app/runs/" + executionId;
  }

  /** Generate a URL to the workflow detail page in the UI. */
  public String pipelineUrl() {
    if (uiBaseUrl == null || pipelineId == null) {
      return null;
    }
    return uiBaseUrl + "/app/workflows/" + pipelineId;
  }

  /** Short summary for notification title. */
  public String shortSummary() {
    return switch (eventType) {
      case "execution.failed" ->
          "Workflow failed: " + (pipelineName != null ? pipelineName : "Unknown");
      case "execution.timeout" ->
          "Workflow timed out: " + (pipelineName != null ? pipelineName : "Unknown");
      case "execution.cancelled" ->
          "Workflow cancelled: " + (pipelineName != null ? pipelineName : "Unknown");
      case "execution.completed" ->
          "Workflow completed: " + (pipelineName != null ? pipelineName : "Unknown");
      case "job.failed" ->
          "Stage failed: "
              + (stageName != null ? stageName : "Unknown")
              + " in "
              + (pipelineName != null ? pipelineName : "workflow");
      default -> "Alert: " + eventType;
    };
  }
}
