package io.pravah.execution.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity for stage checkpoints (US-02.12). */
@Entity
@Table(name = "checkpoints")
@IdClass(CheckpointEntity.CheckpointId.class)
public class CheckpointEntity {

  @Id
  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Id
  @Column(name = "stage_id", nullable = false)
  private String stageId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> state;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CheckpointEntity() {}

  public CheckpointEntity(UUID executionId, String stageId, Map<String, Object> state) {
    this.executionId = Objects.requireNonNull(executionId, "executionId is required");
    this.stageId = Objects.requireNonNull(stageId, "stageId is required");
    this.state = Objects.requireNonNull(state, "state is required");
    this.updatedAt = Instant.now();
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public String getStageId() {
    return stageId;
  }

  public Map<String, Object> getState() {
    return state;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateState(Map<String, Object> newState) {
    this.state = Objects.requireNonNull(newState, "state is required");
    this.updatedAt = Instant.now();
  }

  public record CheckpointId(UUID executionId, String stageId) implements Serializable {}
}
