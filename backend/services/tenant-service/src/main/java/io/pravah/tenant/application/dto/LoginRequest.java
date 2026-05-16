package io.pravah.tenant.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email/password login (US-10.01). */
public record LoginRequest(
    @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password) {}
