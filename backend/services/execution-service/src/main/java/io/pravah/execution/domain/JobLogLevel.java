package io.pravah.execution.domain;

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
      throw new IllegalArgumentException("Log level is required");
    }
    return JobLogLevel.valueOf(value.trim().toUpperCase());
  }
}
