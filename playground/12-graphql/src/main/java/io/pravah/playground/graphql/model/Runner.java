package io.pravah.playground.graphql.model;

public record Runner(
        String id,
        String hostname,
        String region,
        RunnerStatus status
) {
}
