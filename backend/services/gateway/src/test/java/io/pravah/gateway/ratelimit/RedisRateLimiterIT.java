package io.pravah.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pravah.test.containers.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

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
    String key = "ratelimit:test:gateway";

    StepVerifier.create(rateLimiter.isAllowed(key, 2, 2, "tenant"))
        .assertNext(r -> assertThat(r.allowed()).isTrue())
        .verifyComplete();
    StepVerifier.create(rateLimiter.isAllowed(key, 2, 2, "tenant"))
        .assertNext(r -> assertThat(r.allowed()).isTrue())
        .verifyComplete();
    StepVerifier.create(rateLimiter.isAllowed(key, 2, 2, "tenant"))
        .assertNext(r -> assertThat(r.allowed()).isFalse())
        .verifyComplete();
  }
}
