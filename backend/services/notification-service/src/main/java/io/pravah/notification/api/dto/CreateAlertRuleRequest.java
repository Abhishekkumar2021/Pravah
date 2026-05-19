package io.pravah.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAlertRuleRequest(
    UUID pipelineId, // null = tenant-wide rule
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description,
    @NotNull String conditions, // JSON string
    @NotNull String channels, // JSON array string
    Integer dedupWindowSeconds) {

  public int dedupWindowSecondsOrDefault() {
    return dedupWindowSeconds != null ? dedupWindowSeconds : 300;
  }
}
