package io.pravah.tenant.application.dto;

/** Response after self-service registration (US-10.01). User must verify email before sign-in. */
public record RegisterResponse(String email, String message) {}
