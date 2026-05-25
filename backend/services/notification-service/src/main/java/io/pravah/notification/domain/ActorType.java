package io.pravah.notification.domain;

/** Actor types for audit log entries. */
public enum ActorType {
  USER("user"),
  API_TOKEN("api_token"),
  SERVICE("service"),
  SYSTEM("system");

  private final String value;

  ActorType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static ActorType fromValue(String value) {
    for (ActorType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    return SYSTEM;
  }
}
