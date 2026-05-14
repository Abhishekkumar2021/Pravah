package io.pravah.pipeline.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Value Object representing a pipeline's YAML definition as a parsed structure. */
public record PipelineDefinition(Map<String, Object> content) {

  public PipelineDefinition {
    Objects.requireNonNull(content, "Pipeline definition content cannot be null");
    content = Collections.unmodifiableMap(content);
  }

  public static PipelineDefinition of(Map<String, Object> content) {
    return new PipelineDefinition(content);
  }

  public boolean isEmpty() {
    return content.isEmpty();
  }
}
