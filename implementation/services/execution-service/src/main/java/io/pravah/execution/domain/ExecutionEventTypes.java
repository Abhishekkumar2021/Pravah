package io.pravah.execution.domain;

/**
 * Outbox / Kafka event type names for the execution aggregate.
 *
 * <p>Multiple types may share one Kafka topic (e.g. {@code pravah.execution.execution.events}) for
 * ordering per execution; consumers must use {@code eventType} in the payload and stay idempotent
 * on {@code eventId} (ADR-004, LLD §16).
 */
public final class ExecutionEventTypes {

  public static final String EXECUTION_CREATED = "execution.created";

  public static final String EXECUTION_CANCELLED = "execution.cancelled";

  private ExecutionEventTypes() {}
}
