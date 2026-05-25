package io.pravah.gateway.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.gateway.security.GatewayPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Request/response logging filter for observability.
 *
 * <p>Logs structured request metadata including:
 *
 * <ul>
 *   <li>Request path, method, and route
 *   <li>Tenant and user context
 *   <li>Response status and latency
 *   <li>Correlation ID for distributed tracing
 * </ul>
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final String START_TIME_ATTR = "gateway.request.startTime";
  private static final String REQUEST_ID_ATTR = "gateway.request.id";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String requestId =
        request.getHeaders().getFirst(REQUEST_ID_HEADER) != null
            ? request.getHeaders().getFirst(REQUEST_ID_HEADER)
            : UUID.randomUUID().toString();

    exchange.getAttributes().put(START_TIME_ATTR, Instant.now());
    exchange.getAttributes().put(REQUEST_ID_ATTR, requestId);

    ServerHttpRequest mutatedRequest =
        request.mutate().header(REQUEST_ID_HEADER, requestId).build();

    exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

    return chain
        .filter(exchange.mutate().request(mutatedRequest).build())
        .then(
            Mono.fromRunnable(
                () -> {
                  logRequest(exchange, requestId);
                }));
  }

  private void logRequest(ServerWebExchange exchange, String requestId) {
    Instant startTime = exchange.getAttribute(START_TIME_ATTR);
    long durationMs =
        startTime != null ? Duration.between(startTime, Instant.now()).toMillis() : -1;

    ServerHttpRequest request = exchange.getRequest();
    HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

    String tenantId = null;
    String userId = null;

    try {
      GatewayPrincipal principal = exchange.getPrincipal().cast(GatewayPrincipal.class).block();
      if (principal != null) {
        tenantId = principal.tenantId() != null ? principal.tenantId().toString() : null;
        userId = principal.userId() != null ? principal.userId().toString() : null;
      }
    } catch (Exception ignored) {
    }

    String clientIp =
        request.getHeaders().getFirst("X-Forwarded-For") != null
            ? request.getHeaders().getFirst("X-Forwarded-For").split(",")[0].trim()
            : request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

    int statusCode = status != null ? status.value() : 0;

    if (statusCode >= 500) {
      log.error(
          "Request completed",
          kv("request_id", requestId),
          kv("method", request.getMethod()),
          kv("path", request.getPath().value()),
          kv("status", statusCode),
          kv("duration_ms", durationMs),
          kv("route", route != null ? route.getId() : "unknown"),
          kv("tenant_id", tenantId),
          kv("user_id", userId),
          kv("client_ip", clientIp));
    } else if (statusCode >= 400) {
      log.warn(
          "Request completed",
          kv("request_id", requestId),
          kv("method", request.getMethod()),
          kv("path", request.getPath().value()),
          kv("status", statusCode),
          kv("duration_ms", durationMs),
          kv("route", route != null ? route.getId() : "unknown"),
          kv("tenant_id", tenantId),
          kv("user_id", userId),
          kv("client_ip", clientIp));
    } else {
      log.info(
          "Request completed",
          kv("request_id", requestId),
          kv("method", request.getMethod()),
          kv("path", request.getPath().value()),
          kv("status", statusCode),
          kv("duration_ms", durationMs),
          kv("route", route != null ? route.getId() : "unknown"),
          kv("tenant_id", tenantId),
          kv("user_id", userId),
          kv("client_ip", clientIp));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
