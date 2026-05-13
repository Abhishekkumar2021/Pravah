package io.pravah.playground.graphql.model;

public record RunnerMetrics(
        double cpuPercent,
        double memoryPercent,
        int activeJobs,
        int queuedJobs
) {
}
