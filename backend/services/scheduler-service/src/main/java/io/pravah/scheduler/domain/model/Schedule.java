package io.pravah.scheduler.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cron schedule for a pipeline (US-03.01). */
@Entity
@Table(name = "schedules")
public class Schedule {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(nullable = false)
  private String name;

  @Column(name = "cron_expression", nullable = false)
  private String cronExpression;

  @Column(nullable = false)
  private String timezone;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String parameters;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "catchup_policy", nullable = false)
  private String catchupPolicy;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Version private Long version;

  protected Schedule() {}

  private Schedule(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID();
    this.tenantId = Objects.requireNonNull(builder.tenantId);
    this.pipelineId = Objects.requireNonNull(builder.pipelineId);
    this.name = Objects.requireNonNull(builder.name);
    this.cronExpression = Objects.requireNonNull(builder.cronExpression);
    this.timezone = builder.timezone != null ? builder.timezone : "UTC";
    this.parameters = builder.parameters != null ? builder.parameters : "{}";
    this.active = builder.active;
    this.catchupPolicy = builder.catchupPolicy != null ? builder.catchupPolicy : "skip";
    this.nextRunAt = builder.nextRunAt;
    this.lastRunAt = builder.lastRunAt;
    this.createdBy = Objects.requireNonNull(builder.createdBy);
  }

  @PrePersist
  void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
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

  public String getName() {
    return name;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getParameters() {
    return parameters;
  }

  public boolean isActive() {
    return active;
  }

  public String getCatchupPolicy() {
    return catchupPolicy;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void pause() {
    this.active = false;
  }

  public void resume(Instant nextRunAt) {
    this.active = true;
    this.nextRunAt = nextRunAt;
  }

  public void recordTriggeredRun(Instant triggeredAt, Instant nextRunAt) {
    this.lastRunAt = triggeredAt;
    this.nextRunAt = nextRunAt;
  }

  public void setNextRunAt(Instant nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public static class Builder {
    private UUID id;
    private UUID tenantId;
    private UUID pipelineId;
    private String name;
    private String cronExpression;
    private String timezone;
    private String parameters;
    private boolean active = true;
    private String catchupPolicy;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private UUID createdBy;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder pipelineId(UUID pipelineId) {
      this.pipelineId = pipelineId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder cronExpression(String cronExpression) {
      this.cronExpression = cronExpression;
      return this;
    }

    public Builder timezone(String timezone) {
      this.timezone = timezone;
      return this;
    }

    public Builder parameters(String parameters) {
      this.parameters = parameters;
      return this;
    }

    public Builder active(boolean active) {
      this.active = active;
      return this;
    }

    public Builder catchupPolicy(String catchupPolicy) {
      this.catchupPolicy = catchupPolicy;
      return this;
    }

    public Builder nextRunAt(Instant nextRunAt) {
      this.nextRunAt = nextRunAt;
      return this;
    }

    public Builder lastRunAt(Instant lastRunAt) {
      this.lastRunAt = lastRunAt;
      return this;
    }

    public Builder createdBy(UUID createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Schedule build() {
      return new Schedule(this);
    }
  }
}
