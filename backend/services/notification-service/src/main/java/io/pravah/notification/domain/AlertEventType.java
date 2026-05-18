package io.pravah.notification.domain;

import java.util.Set;

/** Alert trigger event types that notification-service listens for. */
public final class AlertEventType {

  public static final String EXECUTION_FAILED = "execution.failed";
  public static final String EXECUTION_TIMEOUT = "execution.timeout";
  public static final String EXECUTION_CANCELLED = "execution.cancelled";
  public static final String EXECUTION_COMPLETED = "execution.completed";
  public static final String JOB_FAILED = "job.failed";

  public static final Set<String> ALL =
      Set.of(
          EXECUTION_FAILED,
          EXECUTION_TIMEOUT,
          EXECUTION_CANCELLED,
          EXECUTION_COMPLETED,
          JOB_FAILED);

  private AlertEventType() {}
}
