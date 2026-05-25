package io.pravah.spring.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Micrometer counter for outbox rows dead-lettered after max Kafka publish retries. */
@Component
@ConditionalOnBean(MeterRegistry.class)
public class OutboxDeadLetterMetrics {

  private final MeterRegistry meterRegistry;

  public OutboxDeadLetterMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String service, String eventType, String topic) {
    Counter.builder("pravah.outbox.dead_lettered")
        .description("Outbox events marked dead-lettered after max publish retries")
        .tag("service", service)
        .tag("event_type", eventType != null ? eventType : "unknown")
        .tag("topic", topic != null ? topic : "unknown")
        .register(meterRegistry)
        .increment();
  }
}
