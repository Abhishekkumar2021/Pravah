package io.pravah.pipeline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tenant-scoped secret references.
 *
 * <p>This table stores metadata about secrets (name, provider, path) but never the actual secret
 * values. Values are resolved at execution time from the configured provider.
 */
@Entity
@Table(name = "tenant_secrets")
public class TenantSecretEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false)
  private UUID id;

  @Version private Long version;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_path", nullable = false)
  private String providerPath;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantSecretEntity() {}

  public TenantSecretEntity(
      UUID tenantId,
      String name,
      String description,
      String provider,
      String providerPath,
      UUID createdBy,
      Instant createdAt) {
    this.tenantId = tenantId;
    this.name = name;
    this.description = description;
    this.provider = provider;
    this.providerPath = providerPath;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderPath() {
    return providerPath;
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

  public void update(String description, String provider, String providerPath) {
    this.description = description;
    this.provider = provider;
    this.providerPath = providerPath;
    this.updatedAt = Instant.now();
  }
}
