package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts tenant context from JWT tokens.
 *
 * <p>Per ADR-013 and ADR-009, this filter:
 *
 * <ul>
 *   <li>Extracts the JWT from the Authorization header
 *   <li>Validates the RS256 token using public key from JWKS
 *   <li>Extracts tenant_id claim and populates TenantContext
 *   <li>Clears TenantContext after request completes
 * </ul>
 *
 * <p>The RlsAspect then uses TenantContext to set the PostgreSQL RLS variable.
 *
 * @see TenantContext
 * @see io.pravah.tenant.infrastructure.persistence.RlsAspect
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenVerifier jwtTokenVerifier;

  public TenantFilter(JwtTokenVerifier jwtTokenVerifier) {
    this.jwtTokenVerifier = jwtTokenVerifier;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);

      if (token != null) {
        try {
          JwtClaims claims = jwtTokenVerifier.validateAndGetClaims(token);
          TenantContext.setCurrentUserId(claims.userId());
          TenantContext.setCurrentTenantId(claims.tenantId());

          var authentication =
              new UsernamePasswordAuthenticationToken(
                  claims.userId().toString(),
                  null,
                  Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
          SecurityContextHolder.getContext().setAuthentication(authentication);

          log.debug(
              "Set tenant context and authentication from JWT",
              kv("user_id", claims.userId()),
              kv("tenant_id", claims.tenantId()));
        } catch (JwtVerificationException e) {
          log.debug("JWT verification failed", kv("error", e.getMessage()));
        }
      }

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
