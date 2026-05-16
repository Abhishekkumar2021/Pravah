package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request password reset email (US-10.01 — delivery not implemented in alpha). */
public record PasswordResetRequest(@NotBlank @Email String email) {}
