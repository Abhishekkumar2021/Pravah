package io.pravah.tenant.application.dto;

/** Access token response plus opaque refresh token for HttpOnly cookie. */
public record LoginResult(AuthTokenResponse access, String refreshToken) {}
