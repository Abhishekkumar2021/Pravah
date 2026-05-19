package io.pravah.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pravah.test.containers.RedisContainerExtension;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@ExtendWith(RedisContainerExtension.class)
class RedisRateLimiterIT {

  private RedisRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(
            RedisContainerExtension.getHost(), RedisContainerExtension.getPort());
    factory.afterPropertiesSet();
    ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
    rateLimiter =
        new RedisRateLimiter(template, new GatewayRateLimitMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void isAllowed_enforcesBurst() {
    // Unique key per run; chain requests back-to-back so refill (1 tok/s) cannot refill between
    // calls (separate StepVerifier runs were flaky on CI when the runner was slow).
    String key = "ratelimit:test:gateway:" + UUID.randomUUID();
    int burst = 2;
    int refillPerSecond = 1;

    var r1 = rateLimiter.isAllowed(key, burst, refillPerSecond, "tenant").block();
    var r2 = rateLimiter.isAllowed(key, burst, refillPerSecond, "tenant").block();
    var r3 = rateLimiter.isAllowed(key, burst, refillPerSecond, "tenant").block();

    assertThat(r1).isNotNull();
    assertThat(r1.allowed()).isTrue();
    assertThat(r2).isNotNull();
    assertThat(r2.allowed()).isTrue();
    assertThat(r3).isNotNull();
    assertThat(r3.allowed()).isFalse();
  }
}
