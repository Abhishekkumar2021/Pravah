package io.pravah.graphql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * WebFlux security for GraphQL service (ADR-009). All GraphQL routes require a valid JWT even
 * before resolvers are implemented.
 */
@Configuration
@EnableWebFluxSecurity
public class GraphQLSecurityConfig {

  @Bean
  public SecurityWebFilterChain graphQLSecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .pathMatchers("/graphiql", "/graphiql/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
  }
}
