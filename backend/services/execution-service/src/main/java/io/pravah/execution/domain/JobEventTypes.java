package io.pravah.execution.domain;

/** Outbox / Kafka event type names for jobs (ADR-002 topic pravah.job.created). */
public final class JobEventTypes {

  public static final String JOB_CREATED = "job.created";

  private JobEventTypes() {}
}
