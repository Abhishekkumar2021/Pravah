package io.pravah.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a team identifier.
 *
 * <p>Team IDs are UUIDs that uniquely identify a team within a tenant.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value
 *     Objects</a>
 */
public record TeamId(UUID value) {

  public TeamId {
    Objects.requireNonNull(value, "Team ID cannot be null");
  }

  public static TeamId of(UUID value) {
    return new TeamId(value);
  }

  public static TeamId of(String value) {
    Objects.requireNonNull(value, "Team ID string cannot be null");
    return new TeamId(UUID.fromString(value));
  }

  public static TeamId generate() {
    return new TeamId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
