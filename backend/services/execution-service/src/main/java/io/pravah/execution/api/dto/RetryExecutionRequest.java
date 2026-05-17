package io.pravah.execution.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for retry-from-stage (US-02.05). */
public record RetryExecutionRequest(@NotBlank String fromStageId) {}
