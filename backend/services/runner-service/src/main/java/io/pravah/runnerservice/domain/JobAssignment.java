package io.pravah.runnerservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Tracks assignment of an execution job to a runner instance. */
@Entity
@Table(name = "job_assignments")
public class JobAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "runner_id", nullable = false)
  private UUID runnerId;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(nullable = false)
  private String status = "ASSIGNED";

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getRunnerId() {
    return runnerId;
  }

  public void setRunnerId(UUID runnerId) {
    this.runnerId = runnerId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public void setExecutionId(UUID executionId) {
    this.executionId = executionId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(Instant assignedAt) {
    this.assignedAt = assignedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
