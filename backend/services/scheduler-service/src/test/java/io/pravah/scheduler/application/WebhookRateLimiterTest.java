package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pravah.spring.ratelimit.RedisTokenBucketRateLimiter;
import io.pravah.spring.ratelimit.TokenBucketResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookRateLimiterTest {

  @Mock private RedisTokenBucketRateLimiter redisLimiter;

  private WebhookRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter = new WebhookRateLimiter(redisLimiter);
  }

  @Test
  void tryAcquire_delegatesToRedisWithWebhookKey() {
    UUID triggerId = UUID.randomUUID();
    when(redisLimiter.tryConsume(
            eq("ratelimit:webhook:" + triggerId),
            eq(60),
            anyDouble(),
            eq("webhook"),
            eq("webhook")))
        .thenReturn(new TokenBucketResult(true, 59, 60, 0));

    TokenBucketResult result = rateLimiter.tryAcquire(triggerId, 60);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  void tryAcquire_returnsDeniedWhenRedisRejects() {
    UUID triggerId = UUID.randomUUID();
    when(redisLimiter.tryConsume(
            eq("ratelimit:webhook:" + triggerId),
            anyInt(),
            anyDouble(),
            eq("webhook"),
            eq("webhook")))
        .thenReturn(new TokenBucketResult(false, 0, 60, 5000));

    assertThat(rateLimiter.tryAcquire(triggerId, 60).allowed()).isFalse();
  }
}
