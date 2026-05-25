package io.pravah.pipeline.infrastructure.outbox;

/**
 * Shared limits for the transactional outbox relay (ADR-004).
 *
 * <p>Must stay in sync with {@link
 * io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository#findUnpublishedForUpdate}
 * JPQL literal.
 */
public final class OutboxPublishPolicy {

  /** After this many failed Kafka publishes, rows are no longer polled (manual intervention). */
  public static final int MAX_PUBLISH_RETRIES = 10;

  private OutboxPublishPolicy() {}
}
