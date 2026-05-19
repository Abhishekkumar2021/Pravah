package io.pravah.gateway.ratelimit;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis-based token bucket rate limiter per ADR-012.
 *
 * <p>Uses an atomic Lua script to implement the token bucket algorithm:
 *
 * <ul>
 *   <li>Tokens refill at a configurable rate per second
 *   <li>Burst capacity allows brief spikes above the steady-state rate
 *   <li>Returns allowed/denied with remaining tokens for Retry-After headers
 * </ul>
 */
@Component
public class RedisRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

  private static final String TOKEN_BUCKET_SCRIPT =
      """
      local key       = KEYS[1]
      local capacity  = tonumber(ARGV[1])
      local refill    = tonumber(ARGV[2])
      local now       = tonumber(ARGV[3])
      local requested = tonumber(ARGV[4])

      local data      = redis.call('HMGET', key, 'tokens', 'last_refill')
      local tokens    = tonumber(data[1]) or capacity
      local last      = tonumber(data[2]) or now

      local elapsed = math.max(0, now - last)
      tokens = math.min(capacity, tokens + elapsed * refill / 1000)

      if tokens >= requested then
          tokens = tokens - requested
          redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
          redis.call('EXPIRE', key, 3600)
          return {1, math.floor(tokens), math.floor(capacity)}
      else
          redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
          redis.call('EXPIRE', key, 3600)
          local wait_ms = math.ceil((requested - tokens) * 1000 / refill)
          return {0, math.floor(tokens), wait_ms}
      end
      """;

  private final ReactiveStringRedisTemplate redisTemplate;
  private final RedisScript<List<?>> script;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.script =
        (RedisScript<List<?>>) (RedisScript) RedisScript.of(TOKEN_BUCKET_SCRIPT, List.class);
  }

  /**
   * Check if a request is allowed under the rate limit.
   *
   * @param key the rate limit key (e.g., "ratelimit:tenant:{tenantId}:api")
   * @param burstCapacity maximum tokens (burst capacity)
   * @param refillPerSecond tokens added per second
   * @return RateLimitResult with allowed status and metadata
   */
  public Mono<RateLimitResult> isAllowed(String key, int burstCapacity, int refillPerSecond) {
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
              @SuppressWarnings("unchecked")
              List<?> values = result;
              boolean allowed = ((Number) values.get(0)).longValue() == 1L;
              long tokensRemaining = ((Number) values.get(1)).longValue();
              long retryAfterMs = allowed ? 0 : ((Number) values.get(2)).longValue();
              return new RateLimitResult(allowed, tokensRemaining, burstCapacity, retryAfterMs);
            })
        .doOnError(
            e ->
                log.warn(
                    "Rate limit check failed for key={}, failing open: {}", key, e.getMessage()))
        .onErrorReturn(new RateLimitResult(true, burstCapacity, burstCapacity, 0));
  }

  public record RateLimitResult(
      boolean allowed, long tokensRemaining, long limit, long retryAfterMs) {

    public int retryAfterSeconds() {
      return (int) Math.ceil(retryAfterMs / 1000.0);
    }
  }
}
