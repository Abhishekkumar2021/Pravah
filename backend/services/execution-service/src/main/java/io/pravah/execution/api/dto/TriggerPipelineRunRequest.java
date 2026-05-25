package io.pravah.execution.api.dto;

import java.util.Map;

/**
 * Request body for {@code POST /api/v1/pipelines/{pipelineId}/runs} (US-03.08).
 *
 * @param pipelineVersion when null, uses the pipeline's current published version
 * @param parameters runtime parameters passed to the execution
 * @param async when true, returns only the run id with HTTP 202 (fire-and-forget)
 */
public record TriggerPipelineRunRequest(
    Integer pipelineVersion, Map<String, Object> parameters, Boolean async) {}
