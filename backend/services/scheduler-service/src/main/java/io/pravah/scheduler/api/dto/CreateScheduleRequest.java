package io.pravah.scheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateScheduleRequest(
    @NotNull UUID pipelineId,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 100) String cronExpression,
    @NotBlank @Size(max = 100) String timezone,
    String catchupPolicy) {}
