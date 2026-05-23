package io.pravah.notification.infrastructure.config;

import io.pravah.spring.security.ApiTenantJwtFilter;
import io.pravah.spring.security.JwtTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Bean
  public ApiTenantJwtFilter apiTenantJwtFilter(
      JwtTokenVerifier jwtTokenVerifier,
      io.pravah.spring.security.OptionalJwtBlocklistChecker blocklistChecker) {
    return new ApiTenantJwtFilter(jwtTokenVerifier, blocklistChecker);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ApiTenantJwtFilter apiTenantJwtFilter) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Health and metrics endpoints open
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    // All API endpoints require authentication
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(apiTenantJwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
