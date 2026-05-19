package io.pravah.spring.ratelimit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Blocking Redis token-bucket rate limiter per ADR-012.
 *
 * <p>Shared by scheduler webhook limits and any servlet-based service. Gateway uses the reactive
 * counterpart with the same Lua script in {@code ratelimit/token_bucket.lua}.
 *
 * <p>Register via {@code @Bean} in a service that configures {@code StringRedisTemplate} (see
 * scheduler {@code SchedulerRateLimitConfiguration}).
 */
@SuppressWarnings("rawtypes")
public class RedisTokenBucketRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

  private final StringRedisTemplate redis;
  private final DefaultRedisScript<List> script;

  private final Optional<RateLimitMetrics> metrics;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisTokenBucketRateLimiter(
      StringRedisTemplate redis, Optional<RateLimitMetrics> metrics) {
    this.redis = redis;
    this.metrics = metrics;
    DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptSource(
        new ResourceScriptSource(new ClassPathResource("ratelimit/token_bucket.lua")));
    redisScript.setResultType(List.class);
    this.script = redisScript;
  }

  /**
   * Attempt to consume tokens from the bucket.
   *
   * @param redisKey Redis key (e.g. {@code ratelimit:webhook:{triggerId}})
   * @param burstCapacity maximum tokens (burst)
   * @param refillPerSecond steady-state refill rate
   * @param layer metric tag: {@code gateway} or {@code webhook}
   * @param keyType metric tag: {@code tenant}, {@code apitoken}, {@code ip}, {@code webhook}
   */
  @SuppressWarnings("unchecked")
  public TokenBucketResult tryConsume(
      String redisKey, int burstCapacity, double refillPerSecond, String layer, String keyType) {
    long nowMs = Instant.now().toEpochMilli();
    try {
      @SuppressWarnings("unchecked")
      List<Number> values =
          redis.execute(
              script,
              List.of(redisKey),
              String.valueOf(burstCapacity),
              String.valueOf(refillPerSecond),
              String.valueOf(nowMs),
              "1");
      if (values == null || values.size() < 3) {
        log.warn("Unexpected rate limit script result", kv("key", redisKey));
        return failOpen(burstCapacity, layer, keyType);
      }
      boolean allowed = values.get(0).longValue() == 1L;
      long remaining = values.get(1).longValue();
      long third = values.get(2).longValue();
      long retryAfterMs = allowed ? 0 : third;
      metrics.ifPresent(m -> m.record(layer, keyType, allowed));
      return new TokenBucketResult(allowed, remaining, burstCapacity, retryAfterMs);
    } catch (Exception e) {
      log.warn(
          "Rate limit check failed, failing open",
          kv("key", redisKey),
          kv("error", e.getMessage()));
      return failOpen(burstCapacity, layer, keyType);
    }
  }

  private TokenBucketResult failOpen(int burstCapacity, String layer, String keyType) {
    metrics.ifPresent(m -> m.record(layer, keyType, true));
    return new TokenBucketResult(true, burstCapacity, burstCapacity, 0);
  }
}
