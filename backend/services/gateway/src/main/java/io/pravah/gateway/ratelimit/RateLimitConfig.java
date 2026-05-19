package io.pravah.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limiting configuration per ADR-012.
 *
 * <p>Uses Redis token bucket algorithm with per-tenant limits.
 */
@Configuration
@ConfigurationProperties(prefix = "pravah.ratelimit")
public class RateLimitConfig {

  private boolean enabled = true;
  private int defaultRequestsPerSecond = 100;
  private int defaultBurstCapacity = 200;
  private int apiTokenRequestsPerSecond = 50;
  private int apiTokenBurstCapacity = 100;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getDefaultRequestsPerSecond() {
    return defaultRequestsPerSecond;
  }

  public void setDefaultRequestsPerSecond(int defaultRequestsPerSecond) {
    this.defaultRequestsPerSecond = defaultRequestsPerSecond;
  }

  public int getDefaultBurstCapacity() {
    return defaultBurstCapacity;
  }

  public void setDefaultBurstCapacity(int defaultBurstCapacity) {
    this.defaultBurstCapacity = defaultBurstCapacity;
  }

  public int getApiTokenRequestsPerSecond() {
    return apiTokenRequestsPerSecond;
  }

  public void setApiTokenRequestsPerSecond(int apiTokenRequestsPerSecond) {
    this.apiTokenRequestsPerSecond = apiTokenRequestsPerSecond;
  }

  public int getApiTokenBurstCapacity() {
    return apiTokenBurstCapacity;
  }

  public void setApiTokenBurstCapacity(int apiTokenBurstCapacity) {
    this.apiTokenBurstCapacity = apiTokenBurstCapacity;
  }
}
