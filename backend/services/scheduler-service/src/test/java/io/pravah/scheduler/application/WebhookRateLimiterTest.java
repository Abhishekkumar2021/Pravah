package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookRateLimiterTest {

  private WebhookRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter = new WebhookRateLimiter();
  }

  @Test
  void tryAcquire_allowsWithinLimit() {
    UUID triggerId = UUID.randomUUID();
    int limit = 5;

    for (int i = 0; i < limit; i++) {
      assertThat(rateLimiter.tryAcquire(triggerId, limit))
          .as("Request %d should be allowed", i + 1)
          .isTrue();
    }
  }

  @Test
  void tryAcquire_rejectsWhenLimitExceeded() {
    UUID triggerId = UUID.randomUUID();
    int limit = 3;

    for (int i = 0; i < limit; i++) {
      rateLimiter.tryAcquire(triggerId, limit);
    }

    assertThat(rateLimiter.tryAcquire(triggerId, limit)).isFalse();
  }

  @Test
  void tryAcquire_tracksTriggersIndependently() {
    UUID trigger1 = UUID.randomUUID();
    UUID trigger2 = UUID.randomUUID();
    int limit = 2;

    assertThat(rateLimiter.tryAcquire(trigger1, limit)).isTrue();
    assertThat(rateLimiter.tryAcquire(trigger1, limit)).isTrue();
    assertThat(rateLimiter.tryAcquire(trigger1, limit)).isFalse();

    assertThat(rateLimiter.tryAcquire(trigger2, limit)).isTrue();
  }

  @Test
  void trackedTriggerCount_countsActiveTriggers() {
    assertThat(rateLimiter.trackedTriggerCount()).isZero();

    UUID trigger1 = UUID.randomUUID();
    UUID trigger2 = UUID.randomUUID();

    rateLimiter.tryAcquire(trigger1, 10);
    assertThat(rateLimiter.trackedTriggerCount()).isEqualTo(1);

    rateLimiter.tryAcquire(trigger2, 10);
    assertThat(rateLimiter.trackedTriggerCount()).isEqualTo(2);

    rateLimiter.tryAcquire(trigger1, 10);
    assertThat(rateLimiter.trackedTriggerCount()).isEqualTo(2);
  }

  @Test
  void cleanup_removesStaleEntries() {
    UUID trigger = UUID.randomUUID();
    rateLimiter.tryAcquire(trigger, 10);
    assertThat(rateLimiter.trackedTriggerCount()).isEqualTo(1);

    rateLimiter.cleanup();
    assertThat(rateLimiter.trackedTriggerCount()).isEqualTo(1);
  }
}
