package io.pravah.notification.domain;

/** In-app notification types for user notification center. */
public enum NotificationType {
  ALERT("alert"),
  INFO("info"),
  SUCCESS("success"),
  WARNING("warning");

  private final String value;

  NotificationType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
