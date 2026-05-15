package io.pravah.tenant.application.dto;

import io.pravah.tenant.domain.model.Tenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new tenant.
 *
 * <p>Creates a new tenant with an initial owner user.
 */
public record CreateTenantRequest(
    @NotBlank(message = "Tenant name is required")
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,
    @NotBlank(message = "Tenant slug is required")
        @Size(min = 2, max = 100, message = "Slug must be between 2 and 100 characters")
        @Pattern(
            regexp = "^[a-z0-9]([a-z0-9-]{0,98}[a-z0-9])?$",
            message =
                "Slug must be lowercase alphanumeric with hyphens, not starting/ending with hyphen")
        String slug,
    Tenant.Tier tier,
    @NotBlank(message = "Owner email is required") @Email(message = "Invalid email format")
        String ownerEmail,
    @NotBlank(message = "Owner name is required")
        @Size(min = 2, max = 255, message = "Owner name must be between 2 and 255 characters")
        String ownerName,
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain at least one lowercase, one uppercase, and one digit")
        String ownerPassword) {}
