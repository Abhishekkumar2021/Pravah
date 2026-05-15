package io.pravah.execution.application;

import java.util.UUID;

/** Well-known runner id for the in-process embedded worker (MVP before external runner agents). */
public final class EmbeddedRunnerIds {

  /** Fixed UUID for the embedded job worker co-located with execution-service. */
  public static final UUID LOCAL = UUID.fromString("00000000-0000-4000-8000-000000000001");

  private EmbeddedRunnerIds() {}
}
