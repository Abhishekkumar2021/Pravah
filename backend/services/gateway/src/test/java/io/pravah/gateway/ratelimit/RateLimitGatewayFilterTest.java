package io.pravah.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.gateway.security.GatewayPrincipal;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RateLimitGatewayFilterTest {

  @Mock private RedisRateLimiter redisRateLimiter;
  @Mock private GatewayFilterChain chain;

  private RateLimitConfig config;
  private RateLimitGatewayFilter filter;

  @BeforeEach
  void setUp() {
    config = new RateLimitConfig();
    config.setEnabled(true);
    config.setDefaultBurstCapacity(10);
    config.setDefaultRequestsPerSecond(10);
    filter = new RateLimitGatewayFilter(redisRateLimiter, config);
    lenient().when(chain.filter(any())).thenReturn(Mono.empty());
  }

  @Test
  void filter_returns429WhenRateLimitExceeded() {
    UUID tenantId = UUID.randomUUID();
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/pipelines").build();
    ServerWebExchange exchange =
        MockServerWebExchange.from(request)
            .mutate()
            .principal(Mono.just(GatewayPrincipal.fromJwt(tenantId, UUID.randomUUID())))
            .build();

    when(redisRateLimiter.isAllowed(
            eq("ratelimit:tenant:" + tenantId), anyInt(), anyInt(), eq("tenant")))
        .thenReturn(Mono.just(new RedisRateLimiter.RateLimitResult(false, 0, 10, 2000)));

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    verify(chain, never()).filter(any());
  }

  @Test
  void filter_skipsActuatorPaths() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
    ServerWebExchange exchange = MockServerWebExchange.from(request);

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    verify(redisRateLimiter, never()).isAllowed(any(), anyInt(), anyInt(), any());
    verify(chain).filter(exchange);
  }

  @Test
  void filter_usesIpKeyWhenAnonymous() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/hooks/trigger")
            .remoteAddress(new InetSocketAddress("203.0.113.10", 8080))
            .build();
    ServerWebExchange exchange = MockServerWebExchange.from(request);

    when(redisRateLimiter.isAllowed(eq("ratelimit:ip:203.0.113.10"), anyInt(), anyInt(), eq("ip")))
        .thenReturn(Mono.just(new RedisRateLimiter.RateLimitResult(true, 9, 10, 0)));

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    verify(chain).filter(exchange);
  }
}
