package io.pravah.gateway.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global authentication filter that extracts principal from JWT or API token.
 *
 * <p>Sets the {@link GatewayPrincipal} on the exchange for downstream filters (rate limiting,
 * logging). Also checks the JWT blocklist for revoked tokens.
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String TENANT_ID_CLAIM = "tenant_id";

  private final ReactiveJwtDecoder jwtDecoder;
  private final JwtBlocklistChecker blocklistChecker;

  public GatewayAuthenticationFilter(
      ReactiveJwtDecoder jwtDecoder, JwtBlocklistChecker blocklistChecker) {
    this.jwtDecoder = jwtDecoder;
    this.blocklistChecker = blocklistChecker;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    if (isPublicPath(path)) {
      return chain.filter(
          exchange.mutate().principal(Mono.just(GatewayPrincipal.anonymous())).build());
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || authHeader.isBlank()) {
      return chain.filter(
          exchange.mutate().principal(Mono.just(GatewayPrincipal.anonymous())).build());
    }

    if (authHeader.startsWith(BEARER_PREFIX)) {
      return authenticateJwt(exchange, chain, authHeader.substring(BEARER_PREFIX.length()));
    }

    return chain.filter(
        exchange.mutate().principal(Mono.just(GatewayPrincipal.anonymous())).build());
  }

  private Mono<Void> authenticateJwt(
      ServerWebExchange exchange, GatewayFilterChain chain, String token) {
    return jwtDecoder
        .decode(token)
        .flatMap(
            jwt -> {
              String jti = jwt.getId();
              if (jti != null) {
                return blocklistChecker
                    .isBlocklisted(jti)
                    .flatMap(
                        blocked -> {
                          if (blocked) {
                            log.info("Rejected blocklisted JWT", kv("jti", jti));
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                          }
                          return continueWithJwt(exchange, chain, jwt);
                        });
              }
              return continueWithJwt(exchange, chain, jwt);
            })
        .onErrorResume(
            e -> {
              log.debug("JWT validation failed: {}", e.getMessage());
              return chain.filter(
                  exchange.mutate().principal(Mono.just(GatewayPrincipal.anonymous())).build());
            });
  }

  private Mono<Void> continueWithJwt(
      ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
    UUID tenantId = extractUuid(jwt, TENANT_ID_CLAIM);
    UUID userId = extractUuid(jwt, "sub");

    GatewayPrincipal principal = GatewayPrincipal.fromJwt(tenantId, userId);

    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-User-Id", userId != null ? userId.toString() : "")
            .build();

    ServerWebExchange mutatedExchange =
        exchange.mutate().request(mutatedRequest).principal(Mono.just(principal)).build();

    return chain.filter(mutatedExchange);
  }

  private UUID extractUuid(Jwt jwt, String claim) {
    Object value = jwt.getClaim(claim);
    if (value instanceof String str) {
      try {
        return UUID.fromString(str);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  private boolean isPublicPath(String path) {
    return path.startsWith("/actuator/")
        || path.equals("/.well-known/jwks.json")
        || path.startsWith("/api/v1/auth/")
        || path.startsWith("/api/v1/hooks/");
  }

  @Override
  public int getOrder() {
    return -200;
  }
}
