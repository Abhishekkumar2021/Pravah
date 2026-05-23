package io.pravah.scheduler.infrastructure.config;

import io.pravah.spring.ratelimit.RateLimitMetrics;
import io.pravah.spring.ratelimit.RedisTokenBucketRateLimiter;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SchedulerRateLimitConfiguration {

  @Bean
  RedisTokenBucketRateLimiter redisTokenBucketRateLimiter(
      StringRedisTemplate redisTemplate,
      Optional<RateLimitMetrics> metrics,
      @Value("${pravah.ratelimit.fail-open:true}") boolean failOpen) {
    return new RedisTokenBucketRateLimiter(redisTemplate, metrics, failOpen);
  }
}
