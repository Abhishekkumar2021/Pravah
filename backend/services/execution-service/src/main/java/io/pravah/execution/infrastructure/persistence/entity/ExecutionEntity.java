package io.pravah.execution.infrastructure.persistence.entity;

import io.pravah.common.domain.ExecutionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the executions table.
 *
 * <p>Represents a single run of a pipeline. State transitions are managed via {@link
 * ExecutionState}.
 *
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Execution DB</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Execution</a>
 */
@Entity
@Table(name = "executions")
public class ExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(name = "pipeline_version", nullable = false)
  private int pipelineVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExecutionState status;

  @Column(name = "trigger_type", nullable = false)
  private String triggerType;

  @Column(name = "triggered_by")
  private UUID triggeredBy;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> parameters;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> definitionSnapshot;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "error_category")
  private String errorCategory;

  @Column(name = "retry_of")
  private UUID retryOf;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(nullable = false)
  private Integer version;

  /** JPA requires a no-arg constructor. */
  protected ExecutionEntity() {}

  private ExecutionEntity(Builder builder) {
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
    this.pipelineId = Objects.requireNonNull(builder.pipelineId, "pipelineId is required");
    this.pipelineVersion = builder.pipelineVersion;
    this.status = builder.status != null ? builder.status : ExecutionState.PENDING;
    this.triggerType = Objects.requireNonNull(builder.triggerType, "triggerType is required");
    this.triggeredBy = builder.triggeredBy;
    this.parameters = builder.parameters != null ? builder.parameters : Map.of();
    this.definitionSnapshot = builder.definitionSnapshot;
    this.retryOf = builder.retryOf;
    this.createdAt = Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public int getPipelineVersion() {
    return pipelineVersion;
  }

  public ExecutionState getStatus() {
    return status;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public UUID getTriggeredBy() {
    return triggeredBy;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Map<String, Object> getDefinitionSnapshot() {
    return definitionSnapshot;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getErrorCategory() {
    return errorCategory;
  }

  public UUID getRetryOf() {
    return retryOf;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Integer getVersion() {
    return version;
  }

  /**
   * Transitions to RUNNING state when the first job starts.
   *
   * @throws IllegalStateException if transition is not allowed
   */
  public void start() {
    this.status = this.status.onStart();
    this.startedAt = Instant.now();
  }

  /**
   * Transitions to SUCCEEDED or FAILED based on job outcomes.
   *
   * @param success true if all jobs succeeded
   * @param errorMessage error message if failed (nullable)
   * @param errorCategory error category if failed (nullable)
   * @throws IllegalStateException if transition is not allowed
   */
  public void complete(boolean success, String errorMessage, String errorCategory) {
    this.status = this.status.onComplete(success);
    this.completedAt = Instant.now();
    if (!success) {
      this.errorMessage = errorMessage;
      this.errorCategory = errorCategory;
    }
  }

  /**
   * Transitions to CANCELLED state.
   *
   * @throws IllegalStateException if transition is not allowed
   */
  public void cancel() {
    this.status = this.status.onCancel();
    this.completedAt = Instant.now();
  }

  /**
   * Transitions to FAILED state due to validation or setup failure.
   *
   * @param errorMessage the error message
   * @param errorCategory the error category
   * @throws IllegalStateException if transition is not allowed
   */
  public void fail(String errorMessage, String errorCategory) {
    this.status = this.status.onFail();
    this.completedAt = Instant.now();
    this.errorMessage = errorMessage;
    this.errorCategory = errorCategory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExecutionEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static class Builder {
    private UUID tenantId;
    private UUID pipelineId;
    private int pipelineVersion;
    private ExecutionState status;
    private String triggerType;
    private UUID triggeredBy;
    private Map<String, Object> parameters;
    private Map<String, Object> definitionSnapshot;
    private UUID retryOf;

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder pipelineId(UUID pipelineId) {
      this.pipelineId = pipelineId;
      return this;
    }

    public Builder pipelineVersion(int pipelineVersion) {
      this.pipelineVersion = pipelineVersion;
      return this;
    }

    public Builder status(ExecutionState status) {
      this.status = status;
      return this;
    }

    public Builder triggerType(String triggerType) {
      this.triggerType = triggerType;
      return this;
    }

    public Builder triggeredBy(UUID triggeredBy) {
      this.triggeredBy = triggeredBy;
      return this;
    }

    public Builder parameters(Map<String, Object> parameters) {
      this.parameters = parameters;
      return this;
    }

    public Builder definitionSnapshot(Map<String, Object> definitionSnapshot) {
      this.definitionSnapshot = definitionSnapshot;
      return this;
    }

    public Builder retryOf(UUID retryOf) {
      this.retryOf = retryOf;
      return this;
    }

    public ExecutionEntity build() {
      return new ExecutionEntity(this);
    }
  }
}
