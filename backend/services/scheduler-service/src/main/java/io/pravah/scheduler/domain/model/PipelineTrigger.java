package io.pravah.scheduler.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Webhook or Kafka trigger for a pipeline (US-03.06, US-03.07). */
@Entity
@Table(name = "pipeline_triggers")
public class PipelineTrigger {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(nullable = false)
  private String name;

  @Column(name = "trigger_type", nullable = false)
  private String triggerType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String config;

  @Column(name = "secret_hash")
  private String secretHash;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "last_triggered_at")
  private Instant lastTriggeredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Version private long version;

  protected PipelineTrigger() {}

  private PipelineTrigger(Builder builder) {
    this.id = UUID.randomUUID();
    this.tenantId = builder.tenantId;
    this.pipelineId = builder.pipelineId;
    this.name = builder.name;
    this.triggerType = builder.triggerType.value();
    this.config = builder.config != null ? builder.config : "{}";
    this.secretHash = builder.secretHash;
    this.enabled = builder.enabled;
    this.createdBy = builder.createdBy;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
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

  public TriggerType getTriggerType() {
    return TriggerType.fromValue(triggerType);
  }

  public String getConfig() {
    return config;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getLastTriggeredAt() {
    return lastTriggeredAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public long getVersion() {
    return version;
  }

  public void recordTriggered() {
    this.lastTriggeredAt = Instant.now();
  }

  public void disable() {
    this.enabled = false;
  }

  public void enable() {
    this.enabled = true;
  }

  public void updateName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    this.name = name.trim();
  }

  public void updateConfig(String config) {
    this.config = config != null ? config : "{}";
  }

  public static class Builder {
    private UUID tenantId;
    private UUID pipelineId;
    private String name;
    private TriggerType triggerType;
    private String config;
    private String secretHash;
    private boolean enabled = true;
    private UUID createdBy;

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

    public Builder triggerType(TriggerType triggerType) {
      this.triggerType = triggerType;
      return this;
    }

    public Builder config(String config) {
      this.config = config;
      return this;
    }

    public Builder secretHash(String secretHash) {
      this.secretHash = secretHash;
      return this;
    }

    public Builder createdBy(UUID createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public PipelineTrigger build() {
      if (tenantId == null) throw new IllegalStateException("tenantId is required");
      if (pipelineId == null) throw new IllegalStateException("pipelineId is required");
      if (name == null || name.isBlank()) throw new IllegalStateException("name is required");
      if (triggerType == null) throw new IllegalStateException("triggerType is required");
      if (createdBy == null) throw new IllegalStateException("createdBy is required");
      return new PipelineTrigger(this);
    }
  }
}
