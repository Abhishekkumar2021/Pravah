package io.pravah.tenant.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-profile-only security for {@code POST /api/v1/auth/dev-token}.
 *
 * <p>Kept separate from {@link SecurityConfig} so production never exposes an unauthenticated token
 * mint path when the {@code local} profile is inactive.
 */
@Configuration
@Profile("local")
public class LocalDevSecurityConfig {

  @Bean
  @Order(0)
  public SecurityFilterChain devTokenSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/v1/auth/dev-token")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }
}
