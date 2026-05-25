package io.pravah.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Alert history - tracks sent notifications for deduplication and audit. */
@Entity
@Table(name = "alert_history")
public class AlertHistoryEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "alert_rule_id", nullable = false)
  private UUID alertRuleId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String eventPayload;

  @Column(name = "delivery_status", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String deliveryStatus = "[]";

  @Column(name = "dedup_key", nullable = false)
  private String dedupKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AlertHistoryEntity() {}

  public AlertHistoryEntity(
      UUID tenantId, UUID alertRuleId, String eventType, String eventPayload, String dedupKey) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.alertRuleId = alertRuleId;
    this.eventType = eventType;
    this.eventPayload = eventPayload;
    this.dedupKey = dedupKey;
    this.createdAt = Instant.now();
    this.deliveryStatus = "[]";
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getAlertRuleId() {
    return alertRuleId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEventPayload() {
    return eventPayload;
  }

  public String getDeliveryStatus() {
    return deliveryStatus;
  }

  public String getDedupKey() {
    return dedupKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setDeliveryStatus(String deliveryStatus) {
    this.deliveryStatus = deliveryStatus;
  }
}
