package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishPipelineRequest(@NotBlank @Size(max = 1_000_000) String definitionYaml) {}
