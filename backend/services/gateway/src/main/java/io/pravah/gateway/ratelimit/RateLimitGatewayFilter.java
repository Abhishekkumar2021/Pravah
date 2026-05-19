package io.pravah.gateway.ratelimit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.gateway.security.GatewayPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global rate limiting filter for all API requests (US-10.14).
 *
 * <p>Implements per-tenant rate limiting using Redis token bucket algorithm. Rate limits are
 * applied based on:
 *
 * <ul>
 *   <li>Tenant ID from JWT claims
 *   <li>API token ID for programmatic access
 *   <li>IP address for unauthenticated requests (e.g., public webhook endpoints)
 * </ul>
 *
 * <p>Returns HTTP 429 with Retry-After header when rate limit is exceeded.
 */
@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RateLimitGatewayFilter.class);
  private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
  private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
  private static final String RETRY_AFTER_HEADER = "Retry-After";

  private final RedisRateLimiter rateLimiter;
  private final RateLimitConfig config;

  public RateLimitGatewayFilter(RedisRateLimiter rateLimiter, RateLimitConfig config) {
    this.rateLimiter = rateLimiter;
    this.config = config;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!config.isEnabled()) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getPath().value();

    if (isExemptPath(path)) {
      return chain.filter(exchange);
    }

    return extractRateLimitKey(exchange)
        .flatMap(
            keyInfo -> {
              int burstCapacity =
                  keyInfo.isApiToken()
                      ? config.getApiTokenBurstCapacity()
                      : config.getDefaultBurstCapacity();
              int refillRate =
                  keyInfo.isApiToken()
                      ? config.getApiTokenRequestsPerSecond()
                      : config.getDefaultRequestsPerSecond();

              return rateLimiter
                  .isAllowed(keyInfo.key(), burstCapacity, refillRate, keyInfo.keyType())
                  .flatMap(
                      result -> {
                        exchange
                            .getResponse()
                            .getHeaders()
                            .add(
                                RATE_LIMIT_REMAINING_HEADER,
                                String.valueOf(result.tokensRemaining()));
                        exchange
                            .getResponse()
                            .getHeaders()
                            .add(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.limit()));

                        if (!result.allowed()) {
                          log.info(
                              "Rate limit exceeded",
                              kv("key", keyInfo.key()),
                              kv("remaining", result.tokensRemaining()),
                              kv("retry_after_seconds", result.retryAfterSeconds()));

                          exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                          exchange
                              .getResponse()
                              .getHeaders()
                              .add(RETRY_AFTER_HEADER, String.valueOf(result.retryAfterSeconds()));
                          return exchange.getResponse().setComplete();
                        }

                        return chain.filter(exchange);
                      });
            });
  }

  private Mono<RateLimitKeyInfo> extractRateLimitKey(ServerWebExchange exchange) {
    return exchange
        .getPrincipal()
        .cast(GatewayPrincipal.class)
        .map(
            principal -> {
              if (principal.apiTokenId() != null) {
                return new RateLimitKeyInfo(
                    "ratelimit:apitoken:" + principal.apiTokenId(), true, "apitoken");
              } else if (principal.tenantId() != null) {
                return new RateLimitKeyInfo(
                    "ratelimit:tenant:" + principal.tenantId(), false, "tenant");
              } else {
                return rateLimitKeyFromIp(exchange);
              }
            })
        .defaultIfEmpty(rateLimitKeyFromIp(exchange));
  }

  private RateLimitKeyInfo rateLimitKeyFromIp(ServerWebExchange exchange) {
    String ip =
        exchange.getRequest().getHeaders().getFirst("X-Forwarded-For") != null
            ? exchange.getRequest().getHeaders().getFirst("X-Forwarded-For").split(",")[0].trim()
            : exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    return new RateLimitKeyInfo("ratelimit:ip:" + ip, false, "ip");
  }

  private boolean isExemptPath(String path) {
    return path.startsWith("/actuator/") || path.equals("/.well-known/jwks.json");
  }

  @Override
  public int getOrder() {
    return -100;
  }

  private record RateLimitKeyInfo(String key, boolean isApiToken, String keyType) {}
}
