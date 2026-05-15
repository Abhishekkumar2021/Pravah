package io.pravah.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing an execution identifier.
 *
 * <p>Execution IDs are UUIDs that uniquely identify a pipeline execution.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value
 *     Objects</a>
 */
public record ExecutionId(UUID value) {

  public ExecutionId {
    Objects.requireNonNull(value, "Execution ID cannot be null");
  }

  public static ExecutionId of(UUID value) {
    return new ExecutionId(value);
  }

  public static ExecutionId of(String value) {
    Objects.requireNonNull(value, "Execution ID string cannot be null");
    return new ExecutionId(UUID.fromString(value));
  }

  public static ExecutionId generate() {
    return new ExecutionId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
