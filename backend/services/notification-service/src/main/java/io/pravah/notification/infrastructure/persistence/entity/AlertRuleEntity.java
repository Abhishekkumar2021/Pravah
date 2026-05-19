package io.pravah.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Alert rule entity - defines when and how to notify. */
@Entity
@Table(name = "alert_rules")
public class AlertRuleEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "pipeline_id")
  private UUID pipelineId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String conditions = "{}";

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String channels = "[]";

  @Column(name = "dedup_window_seconds", nullable = false)
  private int dedupWindowSeconds = 300;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected AlertRuleEntity() {}

  public AlertRuleEntity(
      UUID tenantId,
      UUID pipelineId,
      String name,
      String description,
      String conditions,
      String channels,
      int dedupWindowSeconds,
      UUID createdBy) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.pipelineId = pipelineId;
    this.name = name;
    this.description = description;
    this.conditions = conditions != null ? conditions : "{}";
    this.channels = channels != null ? channels : "[]";
    this.dedupWindowSeconds = dedupWindowSeconds;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.enabled = true;
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

  public String getDescription() {
    return description;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getConditions() {
    return conditions;
  }

  public String getChannels() {
    return channels;
  }

  public int getDedupWindowSeconds() {
    return dedupWindowSeconds;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public void update(
      String name, String description, String conditions, String channels, int dedupWindowSeconds) {
    this.name = name;
    this.description = description;
    this.conditions = conditions != null ? conditions : "{}";
    this.channels = channels != null ? channels : "[]";
    this.dedupWindowSeconds = dedupWindowSeconds;
    this.updatedAt = Instant.now();
  }
}
