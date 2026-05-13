package io.pravah.playground.graphql.model;

import java.time.Instant;
import java.util.List;

public record Execution(
        String id,
        String pipelineId,
        ExecutionStatus status,
        TriggerType triggeredBy,
        Instant startedAt,
        Instant completedAt,
        List<Job> jobs,
        String runnerId
) {
}
