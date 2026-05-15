package io.pravah.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a project identifier.
 *
 * <p>Project IDs are UUIDs that uniquely identify a project within a team.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value
 *     Objects</a>
 */
public record ProjectId(UUID value) {

  public ProjectId {
    Objects.requireNonNull(value, "Project ID cannot be null");
  }

  public static ProjectId of(UUID value) {
    return new ProjectId(value);
  }

  public static ProjectId of(String value) {
    Objects.requireNonNull(value, "Project ID string cannot be null");
    return new ProjectId(UUID.fromString(value));
  }

  public static ProjectId generate() {
    return new ProjectId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
