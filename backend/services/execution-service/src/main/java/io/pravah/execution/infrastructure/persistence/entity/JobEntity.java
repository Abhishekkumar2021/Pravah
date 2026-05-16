package io.pravah.execution.infrastructure.persistence.entity;

import io.pravah.common.domain.JobState;
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
 * JPA entity for the jobs table.
 *
 * <p>Represents a single stage execution within an Execution. State transitions are managed via
 * {@link JobState}.
 *
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Execution DB</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Job</a>
 */
@Entity
@Table(name = "jobs")
public class JobEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false)
  private UUID id;

  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(name = "stage_id", nullable = false)
  private String stageId;

  @Column(name = "stage_name", nullable = false)
  private String stageName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobState status;

  @Column(name = "runner_id")
  private UUID runnerId;

  @Column(nullable = false)
  private int attempt = 1;

  @Column(name = "queued_at")
  private Instant queuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "exit_code")
  private Integer exitCode;

  @Column(name = "error_message")
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> output;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> artifacts;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metrics;

  @Version
  @Column(nullable = false)
  private Integer version;

  /** JPA requires a no-arg constructor. */
  protected JobEntity() {}

  private JobEntity(Builder builder) {
    this.executionId = Objects.requireNonNull(builder.executionId, "executionId is required");
    this.stageId = Objects.requireNonNull(builder.stageId, "stageId is required");
    this.stageName = Objects.requireNonNull(builder.stageName, "stageName is required");
    this.status = builder.status != null ? builder.status : JobState.PENDING;
    this.attempt = builder.attempt > 0 ? builder.attempt : 1;
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public String getStageId() {
    return stageId;
  }

  public String getStageName() {
    return stageName;
  }

  public JobState getStatus() {
    return status;
  }

  public UUID getRunnerId() {
    return runnerId;
  }

  public int getAttempt() {
    return attempt;
  }

  public Instant getQueuedAt() {
    return queuedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Integer getExitCode() {
    return exitCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Map<String, Object> getOutput() {
    return output;
  }

  public Map<String, Object> getArtifacts() {
    return artifacts;
  }

  public Map<String, Object> getMetrics() {
    return metrics;
  }

  public Integer getVersion() {
    return version;
  }

  /**
   * Transitions to QUEUED state when dependencies are met.
   *
   * @throws IllegalStateException if transition is not allowed
   */
  public void queue() {
    this.status = this.status.onDependenciesMet();
    this.queuedAt = Instant.now();
  }

  /**
   * Transitions to SKIPPED state when skip condition is met.
   *
   * @throws IllegalStateException if transition is not allowed
   */
  public void skip() {
    this.status = this.status.onSkip();
    this.completedAt = Instant.now();
  }

  /**
   * Transitions to RUNNING state when assigned to a runner.
   *
   * @param runnerId the assigned runner's ID
   * @throws IllegalStateException if transition is not allowed
   */
  public void assign(UUID runnerId) {
    this.status = this.status.onAssign();
    this.runnerId = Objects.requireNonNull(runnerId, "runnerId is required");
    this.startedAt = Instant.now();
  }

  /**
   * Transitions to SUCCEEDED state.
   *
   * @param exitCode the exit code (typically 0)
   * @param output optional output data
   * @param metrics optional execution metrics
   * @throws IllegalStateException if transition is not allowed
   */
  public void succeed(int exitCode, Map<String, Object> output, Map<String, Object> metrics) {
    this.status = this.status.onSuccess();
    this.exitCode = exitCode;
    this.output = output;
    this.metrics = metrics;
    this.completedAt = Instant.now();
  }

  /**
   * Handles job failure with optional automatic retry (US-02.06).
   *
   * @param exitCode the exit code
   * @param errorMessage the error message
   * @param scheduleRetry when true and the state machine allows, re-queue for another attempt
   * @throws IllegalStateException if transition is not allowed
   */
  public void fail(int exitCode, String errorMessage, boolean scheduleRetry) {
    boolean canRetry = scheduleRetry;
    this.status = this.status.onFailure(canRetry);
    this.exitCode = exitCode;
    this.errorMessage = errorMessage;

    if (canRetry) {
      this.attempt++;
      this.queuedAt = Instant.now();
      this.startedAt = null;
      this.runnerId = null;
    } else {
      this.completedAt = Instant.now();
    }
  }

  /**
   * Transitions to CANCELLED state.
   *
   * @throws IllegalStateException if transition is not allowed
   */
  public void cancel() {
    cancel("Cancelled by user");
  }

  /**
   * Transitions to CANCELLED state and records a human-readable reason (support / UI).
   *
   * @param reason non-blank cancellation reason
   * @throws IllegalStateException if transition is not allowed
   */
  public void cancel(String reason) {
    this.status = this.status.onCancel();
    this.completedAt = Instant.now();
    if (reason != null && !reason.isBlank()) {
      this.errorMessage = reason;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JobEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static class Builder {
    private UUID executionId;
    private String stageId;
    private String stageName;
    private JobState status;
    private int attempt = 1;

    public Builder executionId(UUID executionId) {
      this.executionId = executionId;
      return this;
    }

    public Builder stageId(String stageId) {
      this.stageId = stageId;
      return this;
    }

    public Builder stageName(String stageName) {
      this.stageName = stageName;
      return this;
    }

    public Builder status(JobState status) {
      this.status = status;
      return this;
    }

    public Builder attempt(int attempt) {
      this.attempt = attempt;
      return this;
    }

    public JobEntity build() {
      return new JobEntity(this);
    }
  }
}
