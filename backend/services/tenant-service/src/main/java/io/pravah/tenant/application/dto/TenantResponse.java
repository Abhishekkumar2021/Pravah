package io.pravah.tenant.application.dto;

import io.pravah.tenant.domain.model.Tenant;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for tenant data. */
public record TenantResponse(
    UUID id, String name, String slug, Tenant.Tier tier, Instant createdAt, Instant updatedAt) {

  public static TenantResponse from(Tenant tenant) {
    return new TenantResponse(
        tenant.getId(),
        tenant.getName(),
        tenant.getSlug(),
        tenant.getTier(),
        tenant.getCreatedAt(),
        tenant.getUpdatedAt());
  }
}
