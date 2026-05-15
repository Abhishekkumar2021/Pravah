package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/pipelines/validate} (definition-only check, no persistence).
 */
public record ValidatePipelineRequest(@NotBlank @Size(max = 1_000_000) String definitionYaml) {}
