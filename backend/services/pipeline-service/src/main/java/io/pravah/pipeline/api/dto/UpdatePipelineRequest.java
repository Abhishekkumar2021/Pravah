package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.Size;

public record UpdatePipelineRequest(
    @Size(max = 255) String name,
    @Size(max = 4000) String description,
    @Size(max = 1_000_000) String definitionYaml) {}
