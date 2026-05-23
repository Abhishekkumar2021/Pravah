package io.pravah.gateway.security;

import io.pravah.common.net.IpCidrMatcher;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Optional IP allowlist for gateway ingress (US-10.16). When {@code
 * pravah.gateway.ip-allowlist.enabled=true}, only clients matching configured CIDRs or exact IPs
 * may access the gateway.
 */
@Component
public class IpAllowlistGatewayFilter implements GlobalFilter, Ordered {

  private final boolean enabled;
  private final List<String> allowed;

  public IpAllowlistGatewayFilter(
      @Value("${pravah.gateway.ip-allowlist.enabled:false}") boolean enabled,
      @Value("${pravah.gateway.ip-allowlist.cidrs:}") List<String> allowed) {
    this.enabled = enabled;
    this.allowed =
        allowed != null ? allowed.stream().filter(s -> !s.isBlank()).toList() : List.of();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled || allowed.isEmpty()) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getPath().value();
    if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info")) {
      return chain.filter(exchange);
    }
    String clientIp = resolveClientIp(exchange);
    if (clientIp == null || !isAllowed(clientIp)) {
      exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
      return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
  }

  private static String resolveClientIp(ServerWebExchange exchange) {
    String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
    return remote != null && remote.getAddress() != null
        ? remote.getAddress().getHostAddress()
        : null;
  }

  private boolean isAllowed(String clientIp) {
    for (String rule : allowed) {
      if (IpCidrMatcher.matches(clientIp, rule)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 5;
  }
}
