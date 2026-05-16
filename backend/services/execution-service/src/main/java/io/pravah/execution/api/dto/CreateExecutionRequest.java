package io.pravah.execution.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record CreateExecutionRequest(
    @NotNull UUID pipelineId,
    /** When null, uses the pipeline's current published version. */
    Integer pipelineVersion,
    /** Optional runtime parameters for the execution. */
    Map<String, Object> parameters) {}
