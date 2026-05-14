package io.pravah.execution.application.port;

import java.util.Map;
import java.util.UUID;

/** Resolved published pipeline metadata + definition needed to materialize jobs. */
public record PublishedPipelineSnapshot(
    UUID pipelineId, int pipelineVersion, Map<String, Object> definition, String pipelineStatus) {}
