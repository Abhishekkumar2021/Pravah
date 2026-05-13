package io.pravah.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a role identifier.
 *
 * <p>Role IDs are UUIDs that uniquely identify a role (either system or custom).
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value
 *     Objects</a>
 */
public record RoleId(UUID value) {

  /** System role IDs - predefined roles available to all tenants */
  public static final RoleId OWNER =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  public static final RoleId ADMIN =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  public static final RoleId EDITOR =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  public static final RoleId VIEWER =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000004"));

  public RoleId {
    Objects.requireNonNull(value, "Role ID cannot be null");
  }

  public static RoleId of(UUID value) {
    return new RoleId(value);
  }

  public static RoleId of(String value) {
    Objects.requireNonNull(value, "Role ID string cannot be null");
    return new RoleId(UUID.fromString(value));
  }

  public static RoleId generate() {
    return new RoleId(UUID.randomUUID());
  }

  public boolean isSystemRole() {
    return this.equals(OWNER) || this.equals(ADMIN) || this.equals(EDITOR) || this.equals(VIEWER);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
