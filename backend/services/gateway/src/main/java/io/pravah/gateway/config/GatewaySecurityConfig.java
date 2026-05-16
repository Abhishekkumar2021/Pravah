package io.pravah.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Local-only gateway security: disable CSRF and proxy requests without edge JWT enforcement.
 *
 * <p>JWT validation remains on each backend service (ADR-009). Non-local profiles use Spring
 * Security defaults from {@code spring-boot-starter-oauth2-resource-server}.
 */
@Configuration
@Profile("local")
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain localGatewaySecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .build();
  }
}
