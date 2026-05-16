package io.pravah.execution.domain;

import io.pravah.common.exception.ValidationException;

/** Log level for job log lines (US-02.03). */
public enum JobLogLevel {
  DEBUG,
  INFO,
  WARN,
  ERROR;

  public String asDatabaseValue() {
    return name();
  }

  public static JobLogLevel fromDatabaseValue(String value) {
    if (value == null || value.isBlank()) {
      throw ValidationException.of("level", "Log level is required");
    }
    try {
      return JobLogLevel.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw ValidationException.of("level", "Unknown log level: " + value, value);
    }
  }
}
