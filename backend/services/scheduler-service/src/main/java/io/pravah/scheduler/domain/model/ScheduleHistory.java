package io.pravah.scheduler.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Log of a schedule trigger attempt (US-03.01). */
@Entity
@Table(name = "schedule_history")
public class ScheduleHistory {

  public static final String STATUS_TRIGGERED = "triggered";
  public static final String STATUS_FAILED = "failed";

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "schedule_id", nullable = false)
  private UUID scheduleId;

  @Column(name = "scheduled_time", nullable = false)
  private Instant scheduledTime;

  @Column(name = "execution_id")
  private UUID executionId;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ScheduleHistory() {}

  private ScheduleHistory(
      UUID tenantId, UUID scheduleId, Instant scheduledTime, UUID executionId, String status) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.scheduleId = scheduleId;
    this.scheduledTime = scheduledTime;
    this.executionId = executionId;
    this.status = status;
    this.createdAt = Instant.now();
  }

  public static ScheduleHistory triggered(
      UUID tenantId, UUID scheduleId, Instant scheduledTime, UUID executionId) {
    return new ScheduleHistory(tenantId, scheduleId, scheduledTime, executionId, STATUS_TRIGGERED);
  }

  public static ScheduleHistory failed(UUID tenantId, UUID scheduleId, Instant scheduledTime) {
    return new ScheduleHistory(tenantId, scheduleId, scheduledTime, null, STATUS_FAILED);
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public Instant getScheduledTime() {
    return scheduledTime;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
