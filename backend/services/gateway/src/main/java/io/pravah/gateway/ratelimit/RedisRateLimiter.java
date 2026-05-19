package io.pravah.gateway.ratelimit;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis-based token bucket rate limiter per ADR-012.
 *
 * <p>Uses the shared Lua script {@code ratelimit/token_bucket.lua} from {@code libs:common}.
 */
@Component
@SuppressWarnings("rawtypes")
public class RedisRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

  private final ReactiveStringRedisTemplate redisTemplate;
  private final RedisScript<List> script;
  private final GatewayRateLimitMetrics metrics;

  public RedisRateLimiter(
      ReactiveStringRedisTemplate redisTemplate, GatewayRateLimitMetrics metrics) {
    this.redisTemplate = redisTemplate;
    this.metrics = metrics;
    DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptSource(
        new ResourceScriptSource(new ClassPathResource("ratelimit/token_bucket.lua")));
    redisScript.setResultType(List.class);
    this.script = redisScript;
  }

  /**
   * Check if a request is allowed under the rate limit.
   *
   * @param key the rate limit key (e.g., "ratelimit:tenant:{tenantId}")
   * @param burstCapacity maximum tokens (burst capacity)
   * @param refillPerSecond tokens added per second
   * @param keyType metric tag: tenant, apitoken, or ip
   */
  public Mono<RateLimitResult> isAllowed(
      String key, int burstCapacity, int refillPerSecond, String keyType) {
    long nowMs = Instant.now().toEpochMilli();

    List<String> keys = List.of(key);
    List<String> args =
        Arrays.asList(
            String.valueOf(burstCapacity),
            String.valueOf(refillPerSecond),
            String.valueOf(nowMs),
            "1");

    return redisTemplate
        .execute(script, keys, args)
        .next()
        .map(
            result -> {
              List<?> values = result;
              boolean allowed = ((Number) values.get(0)).longValue() == 1L;
              long tokensRemaining = ((Number) values.get(1)).longValue();
              long retryAfterMs = allowed ? 0 : ((Number) values.get(2)).longValue();
              metrics.record(keyType, allowed);
              return new RateLimitResult(allowed, tokensRemaining, burstCapacity, retryAfterMs);
            })
        .doOnError(
            e -> {
              log.warn("Rate limit check failed for key={}, failing open: {}", key, e.getMessage());
              metrics.record(keyType, true);
            })
        .onErrorReturn(new RateLimitResult(true, burstCapacity, burstCapacity, 0));
  }

  public record RateLimitResult(
      boolean allowed, long tokensRemaining, long limit, long retryAfterMs) {

    public int retryAfterSeconds() {
      return (int) Math.ceil(retryAfterMs / 1000.0);
    }
  }
}
