package io.pravah.tenant.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login lockout and registration defaults (US-10.01). */
@ConfigurationProperties(prefix = "pravah.auth")
public record AuthProperties(
    int maxFailedAttempts,
    Duration lockDuration,
    /** Tenant for self-service registration when no invite context exists (local alpha). */
    java.util.UUID registrationTenantId) {

  public AuthProperties {
    if (maxFailedAttempts < 1) {
      throw new IllegalArgumentException("pravah.auth.max-failed-attempts must be >= 1");
    }
    if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
      throw new IllegalArgumentException("pravah.auth.lock-duration must be positive");
    }
  }
}
