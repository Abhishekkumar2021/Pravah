package io.pravah.pipeline.api.dto;

/** Result of {@code POST /api/v1/pipelines/validate} when the definition passes current checks. */
public record ValidatePipelineResponse(boolean valid) {}
