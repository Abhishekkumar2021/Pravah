package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirms password reset with token from email link (US-10.01). */
public record ConfirmPasswordResetRequest(
    @NotBlank String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
