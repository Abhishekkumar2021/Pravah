package io.pravah.tenant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * API token for programmatic access (ADR-009).
 *
 * <p>Only the SHA-256 hash of the token is stored. The raw value is returned once at creation.
 */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String name;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(columnDefinition = "jsonb", nullable = false)
  private String permissions;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Version private Long version;

  protected ApiToken() {
    // JPA
  }

  private ApiToken(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID();
    this.tenantId = Objects.requireNonNull(builder.tenantId, "Tenant ID is required");
    this.userId = Objects.requireNonNull(builder.userId, "User ID is required");
    this.name = Objects.requireNonNull(builder.name, "Name is required");
    this.tokenHash = Objects.requireNonNull(builder.tokenHash, "Token hash is required");
    this.permissions = builder.permissions != null ? builder.permissions : "[]";
    this.expiresAt = builder.expiresAt;
    this.createdAt = Instant.now();
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

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getPermissions() {
    return permissions;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && expiresAt.isBefore(now);
  }

  public boolean isActive(Instant now) {
    return !isRevoked() && !isExpired(now);
  }

  public void revoke() {
    if (revokedAt == null) {
      this.revokedAt = Instant.now();
    }
  }

  public void recordUsage(Instant usedAt) {
    this.lastUsedAt = usedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ApiToken apiToken = (ApiToken) o;
    return Objects.equals(id, apiToken.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  public static class Builder {
    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private String name;
    private String tokenHash;
    private String permissions;
    private Instant expiresAt;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder userId(UUID userId) {
      this.userId = userId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder tokenHash(String tokenHash) {
      this.tokenHash = tokenHash;
      return this;
    }

    public Builder permissions(String permissions) {
      this.permissions = permissions;
      return this;
    }

    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public ApiToken build() {
      return new ApiToken(this);
    }
  }
}
