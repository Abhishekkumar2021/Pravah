package io.pravah.tenant.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Role entity for permission management.
 *
 * <p>Roles can be system-defined (shared across all tenants) or custom (tenant-specific). System
 * roles have tenant_id = null and is_system = true.
 *
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Tenant Database</a>
 */
@Entity
@Table(name = "roles")
public class Role {

  /** System role: Owner - full access to tenant */
  public static final UUID OWNER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** System role: Admin - administrative access */
  public static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** System role: Editor - can create and modify pipelines */
  public static final UUID EDITOR_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  /** System role: Viewer - read-only access */
  public static final UUID VIEWER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

  @Id private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(columnDefinition = "jsonb", nullable = false)
  private String permissions;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Role() {
    // JPA
  }

  private Role(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID();
    this.tenantId = builder.tenantId;
    this.name = Objects.requireNonNull(builder.name, "Role name is required");
    this.description = builder.description;
    this.permissions = builder.permissions != null ? builder.permissions : "[]";
    this.isSystem = builder.isSystem;
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

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getPermissions() {
    return permissions;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isOwner() {
    return OWNER_ROLE_ID.equals(this.id);
  }

  public boolean isAdmin() {
    return ADMIN_ROLE_ID.equals(this.id);
  }

  public void updateName(String newName) {
    if (this.isSystem) {
      throw new IllegalStateException("Cannot modify system role");
    }
    this.name = Objects.requireNonNull(newName, "Name cannot be null");
  }

  public void updateDescription(String newDescription) {
    if (this.isSystem) {
      throw new IllegalStateException("Cannot modify system role");
    }
    this.description = newDescription;
  }

  public void updatePermissions(String newPermissions) {
    if (this.isSystem) {
      throw new IllegalStateException("Cannot modify system role");
    }
    this.permissions = newPermissions != null ? newPermissions : "[]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Role role)) return false;
    return Objects.equals(id, role.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Role{id=%s, name='%s', isSystem=%s}".formatted(id, name, isSystem);
  }

  public static class Builder {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private String permissions;
    private boolean isSystem;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder permissions(String permissions) {
      this.permissions = permissions;
      return this;
    }

    public Builder isSystem(boolean isSystem) {
      this.isSystem = isSystem;
      return this;
    }

    public Role build() {
      return new Role(this);
    }
  }
}
