package io.pravah.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a runner identifier.
 * <p>
 * Runner IDs are UUIDs generated when a runner is registered with the Runner Service.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Value Objects</a>
 */
public record RunnerId(UUID value) {

    /**
     * Compact constructor with validation.
     */
    public RunnerId {
        Objects.requireNonNull(value, "Runner ID cannot be null");
    }

    /**
     * Factory method for creating a RunnerId from a UUID.
     *
     * @param value the UUID value
     * @return a validated RunnerId
     */
    public static RunnerId of(UUID value) {
        return new RunnerId(value);
    }

    /**
     * Factory method for creating a RunnerId from a string.
     *
     * @param value the UUID string
     * @return a validated RunnerId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static RunnerId of(String value) {
        Objects.requireNonNull(value, "Runner ID string cannot be null");
        return new RunnerId(UUID.fromString(value));
    }

    /**
     * Generate a new random RunnerId.
     *
     * @return a new RunnerId with a random UUID
     */
    public static RunnerId generate() {
        return new RunnerId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
