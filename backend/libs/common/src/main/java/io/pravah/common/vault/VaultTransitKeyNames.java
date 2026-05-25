package io.pravah.common.vault;

import java.util.Objects;
import java.util.UUID;

/** Vault Transit key naming for per-tenant encryption (ADR-007). */
public final class VaultTransitKeyNames {

  private VaultTransitKeyNames() {}

  public static String tenantKey(UUID tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    return "tenant-" + tenantId;
  }
}
