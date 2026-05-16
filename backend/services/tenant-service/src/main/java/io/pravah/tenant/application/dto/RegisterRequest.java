package io.pravah.tenant.application.dto;

import io.pravah.tenant.application.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service registration for local alpha (US-10.01).
 *
 * <p>Enforces strong password requirements at registration.
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @StrongPassword String password,
    @NotBlank @Size(max = 255) String name) {}
