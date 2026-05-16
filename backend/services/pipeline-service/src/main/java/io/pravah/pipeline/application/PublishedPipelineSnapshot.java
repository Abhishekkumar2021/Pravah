package io.pravah.pipeline.application;

import java.util.Map;
import java.util.UUID;

/** Published pipeline metadata for execution or scheduling. */
public record PublishedPipelineSnapshot(
    UUID pipelineId, int pipelineVersion, Map<String, Object> definition, String status) {}
