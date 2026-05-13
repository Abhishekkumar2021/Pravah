package io.pravah.playground.graphql.model;

import java.time.Instant;

public record Job(
        String id,
        String stepId,
        JobStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
