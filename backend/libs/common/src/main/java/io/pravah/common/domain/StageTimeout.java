package io.pravah.common.domain;

import java.time.Instant;

/**
 * Stage execution timeout from pipeline definition (US-02.07).
 *
 * <p>Serialized under pipeline {@code timeout_minutes}/{@code timeout_seconds} or per-stage
 * overrides. See {@code docs/lld/02-database-erd.md}.
 */
public record StageTimeout(Integer timeoutSeconds) {

  /** Maximum allowed timeout (7 days). */
  public static final int MAX_TIMEOUT_SECONDS = 604_800;

  public static final StageTimeout NONE = new StageTimeout(null);

  public StageTimeout {
    if (timeoutSeconds != null) {
      if (timeoutSeconds < 1) {
        throw new IllegalArgumentException("timeout must be >= 1 second");
      }
      if (timeoutSeconds > MAX_TIMEOUT_SECONDS) {
        throw new IllegalArgumentException(
            "timeout must be <= " + MAX_TIMEOUT_SECONDS + " seconds (7 days)");
      }
    }
  }

  public boolean isConfigured() {
    return timeoutSeconds != null;
  }

  /** Returns true when {@code now} is at or past {@code startedAt + timeout}. */
  public boolean isExpired(Instant startedAt, Instant now) {
    if (!isConfigured() || startedAt == null) {
      return false;
    }
    return !startedAt.plusSeconds(timeoutSeconds).isAfter(now);
  }
}
