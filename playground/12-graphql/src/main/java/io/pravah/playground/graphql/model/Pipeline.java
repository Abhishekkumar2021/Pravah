package io.pravah.playground.graphql.model;

import java.time.Instant;
import java.util.List;

public record Pipeline(
        String id,
        String name,
        String description,
        int version,
        String tenantId,
        List<PipelineStep> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
