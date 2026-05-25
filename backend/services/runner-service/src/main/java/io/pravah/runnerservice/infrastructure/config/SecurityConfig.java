package io.pravah.runnerservice.infrastructure.config;

import io.pravah.runnerservice.infrastructure.security.RunnerAgentAuthFilter;
import io.pravah.runnerservice.service.RunnerService;
import io.pravah.spring.security.ApiTenantJwtFilter;
import io.pravah.spring.security.InternalServiceAuthFilter;
import io.pravah.spring.security.JwtTokenVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
  public RunnerAgentAuthFilter runnerAgentAuthFilter(RunnerService runnerService) {
    return new RunnerAgentAuthFilter(runnerService);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      InternalServiceAuthFilter internalServiceAuthFilter,
      RunnerAgentAuthFilter runnerAgentAuthFilter,
      ApiTenantJwtFilter apiTenantJwtFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/api/v1/internal/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/runners/*/jobs/*/environment-secrets")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(runnerAgentAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(apiTenantJwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
