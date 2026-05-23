package io.pravah.gateway.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Micrometer metrics for gateway rate limiting (US-10.14). */
@Component
public class GatewayRateLimitMetrics {

  private final MeterRegistry meterRegistry;

  public GatewayRateLimitMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String keyType, String outcome) {
    Counter.builder("pravah.ratelimit.requests")
        .description("Rate limit checks by layer and outcome")
        .tag("layer", "gateway")
        .tag("key_type", keyType)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }

  public void recordAllowed(String keyType, boolean allowed) {
    record(keyType, allowed ? "allowed" : "denied");
  }
}
