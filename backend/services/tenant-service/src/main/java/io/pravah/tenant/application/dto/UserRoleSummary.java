package io.pravah.tenant.application.dto;

import java.util.UUID;

/** Role assignment summary on a user. */
public record UserRoleSummary(UUID id, String name) {}
