package io.pravah.execution.domain;

/** Outbox / Kafka event type names for the execution aggregate. */
public final class ExecutionEventTypes {

  public static final String EXECUTION_CREATED = "execution.created";

  public static final String EXECUTION_CANCELLED = "execution.cancelled";

  private ExecutionEventTypes() {}
}
