package io.pravah.execution.api.dto;

import java.util.UUID;

/** Minimal response for async pipeline runs (US-03.08). */
public record TriggerPipelineRunResponse(UUID id) {}
