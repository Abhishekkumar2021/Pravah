package io.pravah.tenant.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login lockout, registration, and password reset defaults (US-10.01). */
@ConfigurationProperties(prefix = "pravah.auth")
public record AuthProperties(
    int maxFailedAttempts,
    Duration lockDuration,
    /** Tenant for self-service registration when no invite context exists (local alpha). */
    java.util.UUID registrationTenantId,
    Duration passwordResetTokenTtl,
    String frontendBaseUrl,
    String mailFrom) {

  public AuthProperties {
    if (maxFailedAttempts < 1) {
      throw new IllegalArgumentException("pravah.auth.max-failed-attempts must be >= 1");
    }
    if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
      throw new IllegalArgumentException("pravah.auth.lock-duration must be positive");
    }
    if (passwordResetTokenTtl == null
        || passwordResetTokenTtl.isNegative()
        || passwordResetTokenTtl.isZero()) {
      throw new IllegalArgumentException("pravah.auth.password-reset-token-ttl must be positive");
    }
    if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
      throw new IllegalArgumentException("pravah.auth.frontend-base-url must not be blank");
    }
    if (mailFrom == null || mailFrom.isBlank()) {
      throw new IllegalArgumentException("pravah.auth.mail-from must not be blank");
    }
  }
}
