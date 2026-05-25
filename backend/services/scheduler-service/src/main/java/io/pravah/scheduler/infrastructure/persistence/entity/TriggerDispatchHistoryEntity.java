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
@Table(name = "trigger_dispatch_history")
public class TriggerDispatchHistoryEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "trigger_id", nullable = false)
  private UUID triggerId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(name = "trigger_type", nullable = false)
  private String triggerType;

  @Column(nullable = false)
  private String status;

  @Column(name = "execution_id")
  private UUID executionId;

  @Column(name = "error_message")
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TriggerDispatchHistoryEntity() {}

  public TriggerDispatchHistoryEntity(
      UUID tenantId,
      UUID triggerId,
      UUID pipelineId,
      String triggerType,
      String status,
      UUID executionId,
      String errorMessage,
      String payload) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.triggerId = triggerId;
    this.pipelineId = pipelineId;
    this.triggerType = triggerType;
    this.status = status;
    this.executionId = executionId;
    this.errorMessage = errorMessage;
    this.payload = payload != null ? payload : "{}";
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTriggerId() {
    return triggerId;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getStatus() {
    return status;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
