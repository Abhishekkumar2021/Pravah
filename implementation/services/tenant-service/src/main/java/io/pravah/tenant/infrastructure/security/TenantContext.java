package io.pravah.tenant.infrastructure.security;

import java.util.UUID;

/**
 * Thread-local holder for the current tenant context.
 *
 * <p>This is set by the TenantFilter/Interceptor at the start of each request. The RLS aspect uses
 * this to set the PostgreSQL session variable for Row-Level Security.
 *
 * <p>The tenant ID comes from the authenticated JWT token, never from client-provided request
 * parameters.
 *
 * @see <a href="docs/adr/ADR-013-postgresql-rls-tenant-isolation.md">ADR-013: PostgreSQL RLS</a>
 * @see <a href="docs/adr/ADR-006-pool-multi-tenancy-model.md">ADR-006: Pool Multi-Tenancy</a>
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();
  private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();

  private TenantContext() {}

  /**
   * Sets the current tenant ID for this thread.
   *
   * @param tenantId the tenant UUID (from JWT, never from client input)
   */
  public static void setCurrentTenantId(UUID tenantId) {
    CURRENT_TENANT_ID.set(tenantId);
  }

  /**
   * Gets the current tenant ID for this thread.
   *
   * @return the tenant UUID, or null if not set
   */
  public static UUID getCurrentTenantId() {
    return CURRENT_TENANT_ID.get();
  }

  /**
   * Sets the current user ID for this thread.
   *
   * @param userId the user UUID (from JWT)
   */
  public static void setCurrentUserId(UUID userId) {
    CURRENT_USER_ID.set(userId);
  }

  /**
   * Gets the current user ID for this thread.
   *
   * @return the user UUID, or null if not set
   */
  public static UUID getCurrentUserId() {
    return CURRENT_USER_ID.get();
  }

  /** Clears all context. Call this at the end of each request. */
  public static void clear() {
    CURRENT_TENANT_ID.remove();
    CURRENT_USER_ID.remove();
  }

  /**
   * Checks if a tenant context is set.
   *
   * @return true if tenant context exists
   */
  public static boolean hasTenantContext() {
    return CURRENT_TENANT_ID.get() != null;
  }
}
