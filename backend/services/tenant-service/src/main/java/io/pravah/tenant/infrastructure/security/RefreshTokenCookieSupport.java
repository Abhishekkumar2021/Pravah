package io.pravah.tenant.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** HttpOnly refresh-token cookie per ADR-009. */
@Component
public class RefreshTokenCookieSupport {

  public static final String COOKIE_NAME = "pravah_refresh";

  private final boolean secure;
  private final int maxAgeSeconds;

  public RefreshTokenCookieSupport(
      @Value("${pravah.auth.refresh-cookie.secure:false}") boolean secure,
      @Value("${pravah.auth.refresh-cookie.max-age-seconds:604800}") int maxAgeSeconds) {
    this.secure = secure;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  public ResponseCookie create(String refreshToken) {
    return ResponseCookie.from(COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(maxAgeSeconds)
        .build();
  }

  public ResponseCookie clear() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}
