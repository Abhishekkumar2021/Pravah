package io.pravah.scheduler.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Tracks processed Kafka messages per trigger for idempotent consumer pattern. */
@Entity
@Table(name = "kafka_trigger_processed")
public class KafkaTriggerProcessedEntity {

  @Id private UUID id;

  @Column(name = "trigger_id", nullable = false)
  private UUID triggerId;

  @Column(nullable = false)
  private String topic;

  @Column(name = "partition_num", nullable = false)
  private int partitionNum;

  @Column(name = "offset_num", nullable = false)
  private long offsetNum;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected KafkaTriggerProcessedEntity() {}

  public KafkaTriggerProcessedEntity(
      UUID triggerId, String topic, int partition, long offset, Instant processedAt) {
    this.id = UUID.randomUUID();
    this.triggerId = triggerId;
    this.topic = topic;
    this.partitionNum = partition;
    this.offsetNum = offset;
    this.processedAt = processedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTriggerId() {
    return triggerId;
  }

  public String getTopic() {
    return topic;
  }

  public int getPartitionNum() {
    return partitionNum;
  }

  public long getOffsetNum() {
    return offsetNum;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
