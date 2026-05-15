package io.pravah.pipeline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pipeline_events")
public class PipelineEventEntity {

  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(name = "event_version", nullable = false)
  private int eventVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PipelineEventEntity() {}

  public PipelineEventEntity(
      UUID eventId,
      UUID pipelineId,
      UUID tenantId,
      String eventType,
      int eventVersion,
      Map<String, Object> payload,
      Map<String, Object> metadata,
      Instant createdAt) {
    this.eventId = eventId;
    this.pipelineId = pipelineId;
    this.tenantId = tenantId;
    this.eventType = eventType;
    this.eventVersion = eventVersion;
    this.payload = payload;
    this.metadata = metadata;
    this.createdAt = createdAt;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEventType() {
    return eventType;
  }

  public int getEventVersion() {
    return eventVersion;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
