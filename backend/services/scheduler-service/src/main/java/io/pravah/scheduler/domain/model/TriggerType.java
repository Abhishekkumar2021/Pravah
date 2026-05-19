package io.pravah.scheduler.domain.model;

public enum TriggerType {
  WEBHOOK("webhook"),
  KAFKA("kafka");

  private final String value;

  TriggerType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static TriggerType fromValue(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("trigger type is required");
    }
    for (TriggerType type : values()) {
      if (type.value.equalsIgnoreCase(raw)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown trigger type: " + raw);
  }
}
