package io.pravah.tenant.application.dto;

/** Response when an API token is created; includes the secret exactly once. */
public record CreateApiTokenResponse(ApiTokenResponse token, String secret) {}
