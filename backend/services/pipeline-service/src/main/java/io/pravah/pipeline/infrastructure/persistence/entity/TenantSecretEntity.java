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
 * <p>Pointer providers ({@code env}, {@code vault}) store metadata only. {@code transit} provider
 * stores Vault Transit ciphertext in {@code encrypted_value}; plaintext never persists in Postgres.
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

  @Column(name = "provider_path")
  private String providerPath;

  @Column(name = "encrypted_value")
  private String encryptedValue;

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
    this(tenantId, name, description, provider, providerPath, null, createdBy, createdAt);
  }

  public TenantSecretEntity(
      UUID tenantId,
      String name,
      String description,
      String provider,
      String providerPath,
      String encryptedValue,
      UUID createdBy,
      Instant createdAt) {
    this.tenantId = tenantId;
    this.name = name;
    this.description = description;
    this.provider = provider;
    this.providerPath = providerPath;
    this.encryptedValue = encryptedValue;
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

  public String getEncryptedValue() {
    return encryptedValue;
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
    update(description, provider, providerPath, encryptedValue);
  }

  public void update(
      String description, String provider, String providerPath, String encryptedValue) {
    this.description = description;
    this.provider = provider;
    this.providerPath = providerPath;
    this.encryptedValue = encryptedValue;
    this.updatedAt = Instant.now();
  }
}
