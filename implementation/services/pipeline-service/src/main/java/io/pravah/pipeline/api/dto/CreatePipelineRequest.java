package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePipelineRequest(
    @NotNull UUID projectId,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4000) String description,
    @NotBlank @Size(max = 1_000_000) String definitionYaml) {}
