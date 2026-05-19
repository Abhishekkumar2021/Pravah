package io.pravah.spring.ratelimit;

/** Result of a Redis token-bucket rate limit check (ADR-012). */
public record TokenBucketResult(
    boolean allowed, long tokensRemaining, long limit, long retryAfterMs) {

  public int retryAfterSeconds() {
    return (int) Math.ceil(retryAfterMs / 1000.0);
  }
}
