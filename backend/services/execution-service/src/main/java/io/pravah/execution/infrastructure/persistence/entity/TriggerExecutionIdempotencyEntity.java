package io.pravah.execution.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trigger_execution_idempotency")
@IdClass(TriggerExecutionIdempotencyId.class)
public class TriggerExecutionIdempotencyEntity {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Id
  @Column(name = "idempotency_key", nullable = false, length = 512)
  private String idempotencyKey;

  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TriggerExecutionIdempotencyEntity() {}

  public TriggerExecutionIdempotencyEntity(
      UUID tenantId, String idempotencyKey, UUID executionId, Instant createdAt) {
    this.tenantId = tenantId;
    this.idempotencyKey = idempotencyKey;
    this.executionId = executionId;
    this.createdAt = createdAt;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getExecutionId() {
    return executionId;
  }
}
