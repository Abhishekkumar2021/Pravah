package io.pravah.spring.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pravah.test.containers.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(RedisContainerExtension.class)
class RedisTokenBucketRateLimiterIT {

  private RedisTokenBucketRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(
            RedisContainerExtension.getHost(), RedisContainerExtension.getPort());
    factory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(factory);
    template.afterPropertiesSet();
    rateLimiter =
        new RedisTokenBucketRateLimiter(
            template, java.util.Optional.of(new RateLimitMetrics(new SimpleMeterRegistry())));
  }

  @Test
  void tryConsume_enforcesBurstLimit() {
    String key = "ratelimit:test:burst";
    int burst = 3;
    double refill = 3.0 / 60.0;

    assertThat(rateLimiter.tryConsume(key, burst, refill, "webhook", "webhook").allowed()).isTrue();
    assertThat(rateLimiter.tryConsume(key, burst, refill, "webhook", "webhook").allowed()).isTrue();
    assertThat(rateLimiter.tryConsume(key, burst, refill, "webhook", "webhook").allowed()).isTrue();
    assertThat(rateLimiter.tryConsume(key, burst, refill, "webhook", "webhook").allowed())
        .isFalse();
  }
}
