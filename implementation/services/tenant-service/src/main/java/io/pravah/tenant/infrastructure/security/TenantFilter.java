package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts tenant context from JWT tokens.
 *
 * <p>Per ADR-013 and ADR-009, this filter:
 *
 * <ul>
 *   <li>Extracts the JWT from the Authorization header
 *   <li>Validates the token and extracts tenant_id claim
 *   <li>Populates TenantContext for the request thread
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

  private final JwtTokenProvider jwtTokenProvider;

  public TenantFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);

      if (token != null && jwtTokenProvider.isValidToken(token)) {
        UUID userId = jwtTokenProvider.getUserId(token);
        UUID tenantId = jwtTokenProvider.getTenantId(token);

        TenantContext.setCurrentUserId(userId);
        TenantContext.setCurrentTenantId(tenantId);

        log.debug("Set tenant context from JWT", kv("user_id", userId), kv("tenant_id", tenantId));
      }

      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
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
