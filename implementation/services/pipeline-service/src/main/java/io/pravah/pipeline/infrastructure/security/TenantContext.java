package io.pravah.pipeline.infrastructure.security;

import java.util.UUID;

/**
 * Thread-local tenant and user context populated from the JWT (ADR-009, ADR-013).
 *
 * <p>Tenant and user identifiers are never taken from request bodies or query parameters.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();
  private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();

  private TenantContext() {}

  public static void setCurrentTenantId(UUID tenantId) {
    CURRENT_TENANT_ID.set(tenantId);
  }

  public static UUID getCurrentTenantId() {
    return CURRENT_TENANT_ID.get();
  }

  public static void setCurrentUserId(UUID userId) {
    CURRENT_USER_ID.set(userId);
  }

  public static UUID getCurrentUserId() {
    return CURRENT_USER_ID.get();
  }

  public static void clear() {
    CURRENT_TENANT_ID.remove();
    CURRENT_USER_ID.remove();
  }
}
