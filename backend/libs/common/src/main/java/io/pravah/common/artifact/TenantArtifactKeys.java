package io.pravah.common.artifact;

import java.util.UUID;

/** Validates S3 artifact keys are scoped to a tenant prefix. */
public final class TenantArtifactKeys {

  private TenantArtifactKeys() {}

  public static String tenantPrefix(UUID tenantId) {
    return "tenants/" + tenantId + "/";
  }

  public static String tenantPrefix(String tenantId) {
    return "tenants/" + tenantId + "/";
  }

  public static boolean isOwnedByTenant(String key, UUID tenantId) {
    if (key == null || tenantId == null) {
      return false;
    }
    return key.startsWith(tenantPrefix(tenantId));
  }

  public static boolean isOwnedByTenant(String key, String tenantId) {
    if (key == null || tenantId == null || tenantId.isBlank()) {
      return false;
    }
    return key.startsWith(tenantPrefix(tenantId));
  }

  public static void requireOwnedByTenant(String key, UUID tenantId) {
    if (!isOwnedByTenant(key, tenantId)) {
      throw new IllegalArgumentException("Artifact key is not owned by the current tenant");
    }
  }

  public static void requireOwnedByTenant(String key, String tenantId) {
    if (!isOwnedByTenant(key, tenantId)) {
      throw new IllegalArgumentException("Artifact key is not owned by the current tenant");
    }
  }
}
