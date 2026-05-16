package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void defaults_matchAlphaExpectations() {
    assertThat(RetryPolicy.DEFAULT.maxAttempts()).isEqualTo(3);
    assertThat(RetryPolicy.DEFAULT.delayBeforeAttempt(2)).isZero();
  }

  @Test
  void shouldScheduleRetry_respectsMaxAttemptsAndExitCodes() {
    RetryPolicy policy = new RetryPolicy(3, 0, 1.0, Set.of(1, 2), null);

    assertThat(policy.shouldScheduleRetry(1, 1, Instant.now(), Instant.now())).isTrue();
    assertThat(policy.shouldScheduleRetry(3, 1, Instant.now(), Instant.now())).isFalse();
    assertThat(policy.shouldScheduleRetry(1, 99, Instant.now(), Instant.now())).isFalse();
    assertThat(policy.shouldScheduleRetry(1, 0, Instant.now(), Instant.now())).isFalse();
  }

  @Test
  void shouldScheduleRetry_respectsMaxDuration() {
    RetryPolicy policy = new RetryPolicy(5, 0, 1.0, Set.of(), 60);
    Instant created = Instant.parse("2026-05-16T10:00:00Z");
    Instant within = created.plusSeconds(30);
    Instant beyond = created.plusSeconds(120);

    assertThat(policy.shouldScheduleRetry(1, 1, created, within)).isTrue();
    assertThat(policy.shouldScheduleRetry(1, 1, created, beyond)).isFalse();
  }

  @Test
  void delayBeforeAttempt_exponentialBackoff() {
    RetryPolicy policy = new RetryPolicy(5, 10, 2.0, Set.of(), null);

    assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofSeconds(10));
    assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(policy.delayBeforeAttempt(4)).isEqualTo(Duration.ofSeconds(40));
  }

  @Test
  void parseFromMap_readsPipelineRetryBlock() {
    RetryPolicy policy =
        RetryPolicyParser.parseFromMap(
            Map.of(
                "max_attempts",
                5,
                "delay_seconds",
                2,
                "backoff_multiplier",
                2,
                "retry_on_exit_codes",
                java.util.List.of(1)));

    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.delaySeconds()).isEqualTo(2);
    assertThat(policy.retryOnExitCodes()).containsExactly(1);
  }

  @Test
  void parseFromMap_rejectsInvalidMaxAttempts() {
    assertThatThrownBy(() -> RetryPolicyParser.parseFromMap(Map.of("max_attempts", 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveForStage_mergesPartialOverride() {
    Map<String, Object> definition =
        Map.of(
            "retry",
            Map.of("max_attempts", 5, "delay_seconds", 10),
            "stages",
            java.util.List.of(Map.of("id", "transform", "retry", Map.of("max_attempts", 2))));

    RetryPolicy policy = RetryPolicyParser.resolveForStage(definition, "transform");

    assertThat(policy.maxAttempts()).isEqualTo(2);
    assertThat(policy.delaySeconds()).isEqualTo(10);
  }

  @Test
  void resolveForStage_fallsThroughToPipelineDefault() {
    Map<String, Object> definition =
        Map.of(
            "retry", Map.of("max_attempts", 5, "delay_seconds", 10), "stages", java.util.List.of());

    RetryPolicy policy = RetryPolicyParser.resolveForStage(definition, "unknown-stage");

    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.delaySeconds()).isEqualTo(10);
  }

  @Test
  void delayBeforeAttempt_capsExponentToPreventOverflow() {
    RetryPolicy policy = new RetryPolicy(100, 10, 2.0, Set.of(), null);

    Duration delay = policy.delayBeforeAttempt(50);

    assertThat(delay.getSeconds()).isLessThanOrEqualTo(86_400);
  }
}
