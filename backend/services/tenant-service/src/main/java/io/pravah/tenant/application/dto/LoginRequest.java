package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email/password login (US-10.01).
 *
 * <p>Note: Password validation here only checks basic length constraints. Strong password rules are
 * enforced at registration time via {@link RegisterRequest}, not at login (users with existing weak
 * passwords should still be able to log in).
 */
public record LoginRequest(
    @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password) {}
