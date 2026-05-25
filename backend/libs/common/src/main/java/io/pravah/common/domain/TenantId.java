package io.pravah.common.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

/**
 * Value object representing a tenant identifier.
 *
 * <p>Tenant IDs must be lowercase alphanumeric with hyphens, 3-63 characters. This format ensures
 * compatibility with DNS naming (for subdomains) and PostgreSQL schema naming.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value
 *     Objects</a>
 */
public record TenantId(
    @NotBlank(message = "Tenant ID cannot be blank")
        @Pattern(
            regexp = PATTERN,
            message =
                "Tenant ID must be lowercase alphanumeric with hyphens, 3-63 characters, starting with a letter")
        String value) {
  /**
   * Regex pattern for validating tenant IDs. Lowercase alphanumeric with hyphens, 3-63 characters,
   * must start with a letter and end with alphanumeric.
   */
  public static final String PATTERN = "^[a-z][a-z0-9-]{1,61}[a-z0-9]$";

  /** Compact constructor with validation. */
  public TenantId {
    Objects.requireNonNull(value, "Tenant ID cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Tenant ID cannot be blank");
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Tenant ID must be lowercase alphanumeric with hyphens, 3-63 characters, starting with a letter");
    }
  }

  /**
   * Factory method for creating a TenantId.
   *
   * @param value the tenant identifier string
   * @return a validated TenantId
   * @throws IllegalArgumentException if the value is invalid
   */
  public static TenantId of(String value) {
    return new TenantId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
