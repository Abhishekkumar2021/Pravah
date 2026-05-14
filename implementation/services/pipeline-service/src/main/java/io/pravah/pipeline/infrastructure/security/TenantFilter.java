package io.pravah.pipeline.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates JWT for {@code /api/**} and sets {@link TenantContext} plus Spring Security context.
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
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/");
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
      Claims claims;
      try {
        claims = jwtTokenProvider.validateAndGetClaims(token);
      } catch (JwtException | IllegalArgumentException e) {
        log.debug("JWT validation failed: {}", e.getMessage());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
        return;
      }
      UUID userId = jwtTokenProvider.getUserIdFromClaims(claims);
      UUID tenantId = jwtTokenProvider.getTenantIdFromClaims(claims);
      if (tenantId == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token missing tenant_id claim");
        return;
      }
      TenantContext.setCurrentUserId(userId);
      TenantContext.setCurrentTenantId(tenantId);
      var authentication =
          new UsernamePasswordAuthenticationToken(
              userId.toString(),
              null,
              Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("Authenticated request", kv("user_id", userId), kv("tenant_id", tenantId));
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
