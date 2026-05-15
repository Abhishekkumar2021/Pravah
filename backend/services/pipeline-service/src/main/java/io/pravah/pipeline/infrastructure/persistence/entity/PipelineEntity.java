package io.pravah.pipeline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipelines")
public class PipelineEntity {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(name = "current_version", nullable = false)
  private int currentVersion;

  @Column(nullable = false)
  private String status;

  @Version
  @Column(nullable = false)
  private Integer version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  protected PipelineEntity() {}

  public PipelineEntity(
      UUID id,
      UUID tenantId,
      UUID projectId,
      String name,
      String description,
      int currentVersion,
      String status,
      Instant createdAt,
      Instant updatedAt,
      UUID createdBy) {
    this.id = id;
    this.tenantId = tenantId;
    this.projectId = projectId;
    this.name = name;
    this.description = description;
    this.currentVersion = currentVersion;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getCurrentVersion() {
    return currentVersion;
  }

  public String getStatus() {
    return status;
  }

  public Integer getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setCurrentVersion(int currentVersion) {
    this.currentVersion = currentVersion;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
