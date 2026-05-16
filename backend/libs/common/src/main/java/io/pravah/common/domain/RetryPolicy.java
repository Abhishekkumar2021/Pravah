package io.pravah.common.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Retry configuration for pipeline stages (US-02.06).
 *
 * <p>Serialized in pipeline definition JSON under {@code retry} (pipeline-level) or per-stage
 * {@code stages[].retry}. See {@code docs/lld/02-database-erd.md}.
 */
public record RetryPolicy(
    int maxAttempts,
    int delaySeconds,
    double backoffMultiplier,
    Set<Integer> retryOnExitCodes,
    Integer maxRetryDurationSeconds) {

  public static final int DEFAULT_MAX_ATTEMPTS = 3;

  public static final RetryPolicy DEFAULT =
      new RetryPolicy(DEFAULT_MAX_ATTEMPTS, 0, 1.0, Set.of(), null);

  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("retry.max_attempts must be >= 1");
    }
    if (delaySeconds < 0) {
      throw new IllegalArgumentException("retry.delay_seconds must be >= 0");
    }
    if (backoffMultiplier < 1.0) {
      throw new IllegalArgumentException("retry.backoff_multiplier must be >= 1");
    }
    if (maxRetryDurationSeconds != null && maxRetryDurationSeconds < 1) {
      throw new IllegalArgumentException("retry.max_retry_duration_seconds must be >= 1");
    }
    retryOnExitCodes = retryOnExitCodes == null ? Set.of() : Set.copyOf(retryOnExitCodes);
  }

  /** Whether {@code exitCode} is eligible for an automatic retry. */
  public boolean isRetryableExitCode(int exitCode) {
    if (exitCode == 0) {
      return false;
    }
    if (retryOnExitCodes.isEmpty()) {
      return true;
    }
    return retryOnExitCodes.contains(exitCode);
  }

  /**
   * @param currentAttempt attempt before incrementing for the next run (1 on first failure)
   * @param exitCode process exit code from the failed run
   * @param jobCreatedAt job row creation time for max-duration window
   * @param now clock for duration check
   */
  public boolean shouldScheduleRetry(
      int currentAttempt, int exitCode, Instant jobCreatedAt, Instant now) {
    if (!isRetryableExitCode(exitCode)) {
      return false;
    }
    if (currentAttempt >= maxAttempts) {
      return false;
    }
    if (maxRetryDurationSeconds != null) {
      Duration elapsed = Duration.between(jobCreatedAt, now);
      if (elapsed.getSeconds() > maxRetryDurationSeconds) {
        return false;
      }
    }
    return true;
  }

  private static final int MAX_BACKOFF_EXPONENT = 20;
  private static final long MAX_DELAY_SECONDS = 86_400;

  /**
   * Delay before the next attempt; {@code attemptAfterFail} is the new attempt number (2, 3, …).
   */
  public Duration delayBeforeAttempt(int attemptAfterFail) {
    if (delaySeconds <= 0 || attemptAfterFail < 2) {
      return Duration.ZERO;
    }
    int retryIndex = attemptAfterFail - 2;
    if (backoffMultiplier <= 1.0) {
      return Duration.ofSeconds(delaySeconds);
    }
    int cappedIndex = Math.min(retryIndex, MAX_BACKOFF_EXPONENT);
    double seconds = delaySeconds * Math.pow(backoffMultiplier, cappedIndex);
    long capped = (long) Math.min(seconds, MAX_DELAY_SECONDS);
    return Duration.ofSeconds(Math.max(capped, 0));
  }
}
