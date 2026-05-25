package io.pravah.scheduler.application;

import io.pravah.spring.ratelimit.RedisTokenBucketRateLimiter;
import io.pravah.spring.ratelimit.TokenBucketResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-webhook-trigger rate limiter using Redis token bucket (ADR-012).
 *
 * <p>Distributed across scheduler instances; limits are configured per trigger via {@code
 * rateLimitPerMinute} in trigger config.
 */
@Component
public class WebhookRateLimiter {

  private static final String LAYER = "webhook";
  private static final String KEY_TYPE = "webhook";

  private final RedisTokenBucketRateLimiter rateLimiter;

  public WebhookRateLimiter(RedisTokenBucketRateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  /**
   * @param triggerId webhook trigger id
   * @param limitPerMinute max requests per minute (burst capacity)
   * @return result including whether the request is allowed and retry-after metadata
   */
  public TokenBucketResult tryAcquire(UUID triggerId, int limitPerMinute) {
    String key = "ratelimit:webhook:" + triggerId;
    double refillPerSecond = limitPerMinute / 60.0;
    return rateLimiter.tryConsume(key, limitPerMinute, refillPerSecond, LAYER, KEY_TYPE);
  }
}
