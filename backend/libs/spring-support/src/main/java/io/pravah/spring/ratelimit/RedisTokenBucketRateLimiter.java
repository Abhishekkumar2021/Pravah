package io.pravah.spring.ratelimit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Blocking Redis token-bucket rate limiter per ADR-012.
 *
 * <p>Shared by scheduler webhook limits and any servlet-based service. Gateway uses the reactive
 * counterpart with the same Lua script in {@code ratelimit/token_bucket.lua}.
 */
@SuppressWarnings("rawtypes")
public class RedisTokenBucketRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

  private final StringRedisTemplate redis;
  private final DefaultRedisScript<List> script;
  private final Optional<RateLimitMetrics> metrics;
  private final boolean failOpen;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisTokenBucketRateLimiter(
      StringRedisTemplate redis,
      Optional<RateLimitMetrics> metrics,
      @Value("${pravah.ratelimit.fail-open:true}") boolean failOpen) {
    this.redis = redis;
    this.metrics = metrics;
    this.failOpen = failOpen;
    DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptSource(
        new ResourceScriptSource(new ClassPathResource("ratelimit/token_bucket.lua")));
    redisScript.setResultType(List.class);
    this.script = redisScript;
  }

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
        return onRedisFailure(burstCapacity, layer, keyType);
      }
      boolean allowed = values.get(0).longValue() == 1L;
      long remaining = values.get(1).longValue();
      long third = values.get(2).longValue();
      long retryAfterMs = allowed ? 0 : third;
      metrics.ifPresent(m -> m.recordAllowed(layer, keyType, allowed));
      return new TokenBucketResult(allowed, remaining, burstCapacity, retryAfterMs);
    } catch (Exception e) {
      log.warn(
          failOpen ? "Rate limit check failed, failing open" : "Rate limit check failed, denying",
          kv("key", redisKey),
          kv("error", e.getMessage()));
      return onRedisFailure(burstCapacity, layer, keyType);
    }
  }

  private TokenBucketResult onRedisFailure(int burstCapacity, String layer, String keyType) {
    if (failOpen) {
      metrics.ifPresent(m -> m.record(layer, keyType, "fail_open"));
      return new TokenBucketResult(true, burstCapacity, burstCapacity, 0);
    }
    metrics.ifPresent(m -> m.record(layer, keyType, "denied"));
    return new TokenBucketResult(false, 0, burstCapacity, 1000);
  }
}
