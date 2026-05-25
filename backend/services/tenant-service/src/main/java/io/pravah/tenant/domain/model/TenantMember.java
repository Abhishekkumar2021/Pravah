package io.pravah.tenant.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant membership - maps users to their roles within a tenant.
 *
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Tenant Database</a>
 */
@Entity
@Table(name = "tenant_members")
@IdClass(TenantMemberId.class)
public class TenantMember {

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected TenantMember() {
    // JPA
  }

  public TenantMember(UUID tenantId, UUID userId, UUID roleId) {
    this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID is required");
    this.userId = Objects.requireNonNull(userId, "User ID is required");
    this.roleId = Objects.requireNonNull(roleId, "Role ID is required");
    this.joinedAt = Instant.now();
  }

  public static TenantMember createOwner(UUID tenantId, UUID userId) {
    return new TenantMember(tenantId, userId, Role.OWNER_ROLE_ID);
  }

  public static TenantMember createAdmin(UUID tenantId, UUID userId) {
    return new TenantMember(tenantId, userId, Role.ADMIN_ROLE_ID);
  }

  public static TenantMember createEditor(UUID tenantId, UUID userId) {
    return new TenantMember(tenantId, userId, Role.EDITOR_ROLE_ID);
  }

  public static TenantMember createViewer(UUID tenantId, UUID userId) {
    return new TenantMember(tenantId, userId, Role.VIEWER_ROLE_ID);
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void changeRole(UUID newRoleId) {
    this.roleId = Objects.requireNonNull(newRoleId, "Role ID cannot be null");
  }

  public boolean isOwner() {
    return Role.OWNER_ROLE_ID.equals(roleId);
  }

  public boolean isAdmin() {
    return Role.ADMIN_ROLE_ID.equals(roleId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantMember that)) return false;
    return Objects.equals(tenantId, that.tenantId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, userId);
  }

  @Override
  public String toString() {
    return "TenantMember{tenantId=%s, userId=%s, roleId=%s}".formatted(tenantId, userId, roleId);
  }
}
