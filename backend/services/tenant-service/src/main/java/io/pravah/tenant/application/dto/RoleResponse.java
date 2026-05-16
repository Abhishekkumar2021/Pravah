package io.pravah.tenant.application.dto;

import io.pravah.tenant.application.security.RolePermissions;
import io.pravah.tenant.domain.model.Role;
import java.util.List;
import java.util.UUID;

/** Built-in or custom role exposed to admins. */
public record RoleResponse(
    UUID id, String name, String description, List<String> permissions, boolean system) {

  public static RoleResponse from(Role role) {
    return new RoleResponse(
        role.getId(),
        role.getName(),
        role.getDescription(),
        RolePermissions.parse(role.getPermissions()),
        role.isSystem());
  }
}
