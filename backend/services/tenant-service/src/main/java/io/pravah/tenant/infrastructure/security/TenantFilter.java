package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import io.pravah.spring.security.SecurityAuthorities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts tenant context from JWT or API tokens.
 *
 * <p>Per ADR-013 and ADR-009, this filter:
 *
 * <ul>
 *   <li>Extracts the bearer credential from the Authorization header
 *   <li>Validates {@code prv_1_} API tokens (hashed lookup) or RS256 JWTs via JWKS
 *   <li>Populates {@link TenantContext} and Spring Security authorities
 *   <li>Clears context after the request completes
 * </ul>
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
  private final ApiTokenAuthenticator apiTokenAuthenticator;

  public TenantFilter(
      JwtTokenVerifier jwtTokenVerifier, ApiTokenAuthenticator apiTokenAuthenticator) {
    this.jwtTokenVerifier = jwtTokenVerifier;
    this.apiTokenAuthenticator = apiTokenAuthenticator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);

      if (token != null) {
        if (ApiTokenGenerator.isApiToken(token)) {
          authenticateApiToken(token);
        } else {
          authenticateJwt(token);
        }
      }

      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private void authenticateApiToken(String rawToken) {
    apiTokenAuthenticator
        .authenticate(rawToken)
        .ifPresent(
            auth -> {
              TenantContext.setCurrentUserId(auth.userId());
              TenantContext.setCurrentTenantId(auth.tenantId());

              var authentication =
                  new UsernamePasswordAuthenticationToken(
                      auth.userId().toString(),
                      null,
                      SecurityAuthorities.fromJwtPermissions(auth.permissions()));
              SecurityContextHolder.getContext().setAuthentication(authentication);

              log.debug(
                  "Authenticated API token",
                  kv("token_id", auth.tokenId()),
                  kv("user_id", auth.userId()),
                  kv("tenant_id", auth.tenantId()));
            });
  }

  private void authenticateJwt(String token) {
    try {
      JwtClaims claims = jwtTokenVerifier.validateAndGetClaims(token);
      TenantContext.setCurrentUserId(claims.userId());
      TenantContext.setCurrentTenantId(claims.tenantId());

      var authentication =
          new UsernamePasswordAuthenticationToken(
              claims.userId().toString(),
              null,
              SecurityAuthorities.fromJwtPermissions(claims.permissions()));
      SecurityContextHolder.getContext().setAuthentication(authentication);

      log.debug(
          "Set tenant context and authentication from JWT",
          kv("user_id", claims.userId()),
          kv("tenant_id", claims.tenantId()));
    } catch (JwtVerificationException e) {
      log.debug("JWT verification failed", kv("error", e.getMessage()));
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
