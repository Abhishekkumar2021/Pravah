package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to change a user's tenant-level role. */
public record AssignRoleRequest(@NotNull(message = "roleId is required") UUID roleId) {}
