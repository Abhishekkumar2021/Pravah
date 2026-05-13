package io.pravah.tenant.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for TenantMember. */
public class TenantMemberId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID tenantId;
  private UUID userId;

  public TenantMemberId() {}

  public TenantMemberId(UUID tenantId, UUID userId) {
    this.tenantId = tenantId;
    this.userId = userId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantMemberId that)) return false;
    return Objects.equals(tenantId, that.tenantId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, userId);
  }
}
