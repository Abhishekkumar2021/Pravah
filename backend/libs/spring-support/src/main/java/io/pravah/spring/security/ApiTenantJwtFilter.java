package io.pravah.spring.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a valid Bearer JWT for {@code /api/**}, populates {@link TenantContext} and Spring
 * {@code SecurityContext}.
 *
 * <p>Per ADR-009, tokens are validated using RS256 with public keys fetched from the JWKS endpoint.
 * Registered explicitly per service {@code SecurityConfig} (pipeline, execution) — not a
 * {@code @Component} so tenant-service can expose public {@code /api/v1/auth/**} routes using
 * service-specific filters and {@code permitAll} rules.
 *
 * @see JwtTokenVerifier
 * @see io.pravah.spring.multitenancy.TenantContext
 */
public class ApiTenantJwtFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ApiTenantJwtFilter.class);
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenVerifier jwtTokenVerifier;

  public ApiTenantJwtFilter(JwtTokenVerifier jwtTokenVerifier) {
    this.jwtTokenVerifier = jwtTokenVerifier;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/")) {
      return true;
    }
    // Public auth and tenant registration (tenant-service SecurityConfig permitAll).
    if (path.startsWith("/api/v1/auth/")) {
      return true;
    }
    return "POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/tenants".equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);
      if (token == null || token.isBlank()) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
        return;
      }

      JwtClaims claims;
      try {
        claims = jwtTokenVerifier.validateAndGetClaims(token);
      } catch (JwtVerificationException e) {
        log.debug("JWT validation failed", kv("error", e.getMessage()));
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
        return;
      }

      TenantContext.setCurrentUserId(claims.userId());
      TenantContext.setCurrentTenantId(claims.tenantId());

      var authentication =
          new UsernamePasswordAuthenticationToken(
              claims.userId().toString(),
              null,
              SecurityAuthorities.fromJwtPermissions(claims.permissions()));
      SecurityContextHolder.getContext().setAuthentication(authentication);

      log.debug(
          "Authenticated request",
          kv("user_id", claims.userId()),
          kv("tenant_id", claims.tenantId()));
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
