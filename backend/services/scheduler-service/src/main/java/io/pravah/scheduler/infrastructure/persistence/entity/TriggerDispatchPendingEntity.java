package io.pravah.scheduler.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trigger_dispatch_pending")
public class TriggerDispatchPendingEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "trigger_id", nullable = false)
  private UUID triggerId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(name = "trigger_type", nullable = false)
  private String triggerType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_retry_at", nullable = false)
  private Instant nextRetryAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TriggerDispatchPendingEntity() {}

  public static TriggerDispatchPendingEntity pending(
      UUID tenantId,
      UUID triggerId,
      UUID pipelineId,
      String triggerType,
      String payload,
      String idempotencyKey) {
    TriggerDispatchPendingEntity entity = new TriggerDispatchPendingEntity();
    entity.id = UUID.randomUUID();
    entity.tenantId = tenantId;
    entity.triggerId = triggerId;
    entity.pipelineId = pipelineId;
    entity.triggerType = triggerType;
    entity.payload = payload != null ? payload : "{}";
    entity.idempotencyKey = idempotencyKey;
    entity.status = "pending";
    entity.attempts = 0;
    entity.nextRetryAt = Instant.now();
    entity.createdAt = Instant.now();
    entity.updatedAt = Instant.now();
    return entity;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getTriggerId() {
    return triggerId;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getPayload() {
    return payload;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextRetryAt() {
    return nextRetryAt;
  }

  public void markCompleted() {
    this.status = "completed";
    this.updatedAt = Instant.now();
  }

  public void markExhausted() {
    this.status = "exhausted";
    this.updatedAt = Instant.now();
  }

  public void markFailed(String error, Instant nextRetry) {
    this.attempts++;
    this.lastError = error;
    this.status = "failed";
    this.nextRetryAt = nextRetry;
    this.updatedAt = Instant.now();
  }
}
