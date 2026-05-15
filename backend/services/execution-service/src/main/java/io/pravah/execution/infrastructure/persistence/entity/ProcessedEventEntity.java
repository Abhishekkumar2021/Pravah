package io.pravah.execution.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks processed event IDs for idempotent consumer pattern (LLD §16).
 *
 * <p>Before processing a Kafka message, consumers check if the event ID exists in this table. If
 * so, the message is a duplicate and should be skipped.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedEventEntity() {}

  public ProcessedEventEntity(UUID eventId, String eventType, Instant processedAt) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
