package io.pravah.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security configuration.
 *
 * <p>The gateway handles authentication at the edge via custom filters ({@link
 * io.pravah.gateway.security.GatewayAuthenticationFilter}). Spring Security is configured to permit
 * all requests because:
 *
 * <ul>
 *   <li>Authentication is handled by custom filters that extract JWT claims
 *   <li>Authorization is enforced by backend services (defense in depth)
 *   <li>Rate limiting and blocklist checks are performed by gateway filters
 * </ul>
 *
 * @see io.pravah.gateway.security.GatewayAuthenticationFilter
 * @see io.pravah.gateway.ratelimit.RateLimitGatewayFilter
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain gatewaySecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .build();
  }
}
