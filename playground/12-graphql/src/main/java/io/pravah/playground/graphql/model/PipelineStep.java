package io.pravah.playground.graphql.model;

import java.util.List;

public record PipelineStep(
        String id,
        String name,
        StepType stepType,
        List<String> dependsOn
) {
}
