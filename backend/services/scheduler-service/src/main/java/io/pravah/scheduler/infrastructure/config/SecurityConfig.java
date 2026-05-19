package io.pravah.scheduler.infrastructure.config;

import io.pravah.spring.security.ApiTenantJwtFilter;
import io.pravah.spring.security.JwtTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public ApiTenantJwtFilter apiTenantJwtFilter(JwtTokenVerifier jwtTokenVerifier) {
    return new ApiTenantJwtFilter(jwtTokenVerifier);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ApiTenantJwtFilter apiTenantJwtFilter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/v1/hooks/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(apiTenantJwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
