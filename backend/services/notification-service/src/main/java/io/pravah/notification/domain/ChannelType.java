package io.pravah.notification.domain;

import java.util.Set;

/** Supported notification channel types. */
public enum ChannelType {
  EMAIL("email"),
  SLACK("slack"),
  WEBHOOK("webhook");

  private static final Set<String> SUPPORTED = Set.of("email", "slack", "webhook");

  private final String value;

  ChannelType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static ChannelType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Channel type is required");
    }
    String normalized = raw.toLowerCase().trim();
    if (!SUPPORTED.contains(normalized)) {
      throw new IllegalArgumentException(
          "Unsupported channel type '%s'; supported: %s".formatted(raw, SUPPORTED));
    }
    return switch (normalized) {
      case "email" -> EMAIL;
      case "slack" -> SLACK;
      case "webhook" -> WEBHOOK;
      default -> throw new IllegalStateException("Unexpected channel type: " + normalized);
    };
  }
}
