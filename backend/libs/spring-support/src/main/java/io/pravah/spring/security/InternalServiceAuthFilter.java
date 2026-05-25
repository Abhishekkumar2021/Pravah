package io.pravah.spring.security;

import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates service-to-service calls on {@code /api/v1/internal/**} using a shared secret and
 * tenant header. Register as a {@code @Bean} in each service's {@code SecurityConfig}.
 */
@Order(0)
public class InternalServiceAuthFilter extends OncePerRequestFilter {

  public static final String SECRET_HEADER = "X-Pravah-Internal-Secret";
  public static final String TENANT_HEADER = "X-Pravah-Tenant-Id";
  private static final String INTERNAL_PREFIX = "/api/v1/internal/";

  private final String internalSecret;

  public InternalServiceAuthFilter(
      @Value("${pravah.internal-service.secret:}") String internalSecret) {
    this.internalSecret = internalSecret;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null || !path.startsWith(INTERNAL_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      if (internalSecret == null
          || internalSecret.isBlank()
          || !constantTimeEquals(internalSecret, request.getHeader(SECRET_HEADER))) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service secret");
        return;
      }
      String tenantHeader = request.getHeader(TENANT_HEADER);
      if (tenantHeader == null || tenantHeader.isBlank()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing tenant header");
        return;
      }
      UUID tenantId;
      try {
        tenantId = UUID.fromString(tenantHeader.trim());
      } catch (IllegalArgumentException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid tenant header");
        return;
      }
      TenantContext.setCurrentTenantId(tenantId);

      var authentication =
          new UsernamePasswordAuthenticationToken("internal-service", null, java.util.List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);

      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private static boolean constantTimeEquals(String expected, String provided) {
    if (expected == null || provided == null) {
      return false;
    }
    byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] b = provided.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return java.security.MessageDigest.isEqual(a, b);
  }
}
