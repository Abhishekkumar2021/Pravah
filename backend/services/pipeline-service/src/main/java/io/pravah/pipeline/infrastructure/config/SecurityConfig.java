package io.pravah.pipeline.infrastructure.config;

import io.pravah.spring.security.ApiTenantJwtFilter;
import io.pravah.spring.security.InternalServiceAuthFilter;
import io.pravah.spring.security.JwtTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
  public InternalServiceAuthFilter internalServiceAuthFilter(
      @Value("${pravah.internal-service.secret:}") String internalSecret) {
    return new InternalServiceAuthFilter(internalSecret);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      InternalServiceAuthFilter internalServiceAuthFilter,
      ApiTenantJwtFilter apiTenantJwtFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/v1/internal/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(apiTenantJwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
