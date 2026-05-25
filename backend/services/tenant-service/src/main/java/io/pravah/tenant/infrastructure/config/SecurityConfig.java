package io.pravah.tenant.infrastructure.config;

import io.pravah.tenant.infrastructure.logging.MdcLoggingFilter;
import io.pravah.tenant.infrastructure.security.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Tenant Service.
 *
 * <p>Implements stateless JWT-based authentication per ADR-009. Key features:
 *
 * <ul>
 *   <li>Stateless session management (no server-side sessions)
 *   <li>JWT token validation via TenantFilter
 *   <li>Method-level authorization via @PreAuthorize
 *   <li>CSRF disabled (stateless API)
 * </ul>
 *
 * @see <a href="docs/adr/ADR-009-jwt-oauth2-authentication.md">ADR-009: JWT Authentication</a>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final TenantFilter tenantFilter;
  private final MdcLoggingFilter mdcLoggingFilter;

  public SecurityConfig(TenantFilter tenantFilter, MdcLoggingFilter mdcLoggingFilter) {
    this.tenantFilter = tenantFilter;
    this.mdcLoggingFilter = mdcLoggingFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/.well-known/jwks.json")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/verify-email")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/verify-email/resend")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/password-reset/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mdcLoggingFilter, TenantFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
