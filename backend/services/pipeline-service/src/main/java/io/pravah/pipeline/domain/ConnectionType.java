package io.pravah.pipeline.domain;

import java.util.Locale;
import java.util.Set;

/** Supported connection types for alpha. */
public enum ConnectionType {
  POSTGRES("postgres");

  private static final Set<String> SUPPORTED = Set.of(POSTGRES.value);

  private final String value;

  ConnectionType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static String parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Connection type is required");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (!SUPPORTED.contains(normalized)) {
      throw new IllegalArgumentException(
          "Unsupported connection type '%s'; supported: %s".formatted(raw, SUPPORTED));
    }
    return normalized;
  }
}
