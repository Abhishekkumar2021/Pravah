package io.pravah.tenant.domain.model;

import io.pravah.tenant.infrastructure.persistence.TenantTierType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

/**
 * Tenant entity - the top-level isolation boundary in Pravah.
 *
 * <p>All resources (pipelines, executions, users) belong to a tenant. This is the aggregate root
 * for tenant management. Per ADR-006 and ADR-013, tenant_id is used consistently for multi-tenant
 * isolation.
 *
 * @see <a href="../../../../../../docs/adr/ADR-006-pool-multi-tenancy-model.md">ADR-006</a>
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Tenant Database</a>
 */
@Entity
@Table(name = "tenants")
public class Tenant {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Type(TenantTierType.class)
  @Column(nullable = false, columnDefinition = "tenant_tier")
  private Tier tier;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String settings;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected Tenant() {
    // JPA
  }

  private Tenant(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID();
    this.name = Objects.requireNonNull(builder.name, "Tenant name is required");
    this.slug = Objects.requireNonNull(builder.slug, "Tenant slug is required");
    this.tier = builder.tier != null ? builder.tier : Tier.FREE;
    this.settings = builder.settings != null ? builder.settings : "{}";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public Tier getTier() {
    return tier;
  }

  public String getSettings() {
    return settings;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateName(String newName) {
    this.name = Objects.requireNonNull(newName, "Name cannot be null");
    this.updatedAt = Instant.now();
  }

  public void updateTier(Tier newTier) {
    this.tier = Objects.requireNonNull(newTier, "Tier cannot be null");
    this.updatedAt = Instant.now();
  }

  public void updateSettings(String newSettings) {
    this.settings = newSettings != null ? newSettings : "{}";
    this.updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Tenant that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Tenant{id=%s, name='%s', slug='%s', tier=%s}".formatted(id, name, slug, tier);
  }

  /** Subscription tier for tenants. */
  public enum Tier {
    FREE,
    TEAM,
    ENTERPRISE
  }

  public static class Builder {
    private UUID id;
    private String name;
    private String slug;
    private Tier tier;
    private String settings;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder slug(String slug) {
      this.slug = slug;
      return this;
    }

    public Builder tier(Tier tier) {
      this.tier = tier;
      return this;
    }

    public Builder settings(String settings) {
      this.settings = settings;
      return this;
    }

    public Tenant build() {
      return new Tenant(this);
    }
  }
}
