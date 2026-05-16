package io.pravah.scheduler.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CronPreviewRequest(
    @NotBlank @Size(max = 100) String cronExpression,
    @NotBlank @Size(max = 100) String timezone,
    Instant after,
    @Min(1) @Max(10) Integer count) {}
