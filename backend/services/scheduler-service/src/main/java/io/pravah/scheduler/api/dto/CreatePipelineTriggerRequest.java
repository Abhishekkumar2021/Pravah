package io.pravah.scheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.UUID;

public record CreatePipelineTriggerRequest(
    @NotNull UUID pipelineId,
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "webhook|kafka", flags = Pattern.Flag.CASE_INSENSITIVE)
        String triggerType,
    Map<String, Object> config) {}
