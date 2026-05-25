package io.pravah.spring.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for rate limiting (US-10.14 monitoring).
 *
 * <p>Exposed on {@code /actuator/prometheus} as {@code pravah_ratelimit_requests_total} with tags
 * {@code layer} (gateway|webhook) and {@code outcome} (allowed|denied).
 */
@Component
@ConditionalOnBean(MeterRegistry.class)
public class RateLimitMetrics {

  private final MeterRegistry meterRegistry;

  public RateLimitMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String layer, String keyType, String outcome) {
    Counter.builder("pravah.ratelimit.requests")
        .description("Rate limit checks by layer and outcome")
        .tag("layer", layer)
        .tag("key_type", keyType)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }

  public void recordAllowed(String layer, String keyType, boolean allowed) {
    record(layer, keyType, allowed ? "allowed" : "denied");
  }
}
