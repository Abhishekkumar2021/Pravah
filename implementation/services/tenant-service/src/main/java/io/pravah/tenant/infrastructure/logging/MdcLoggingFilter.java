package io.pravah.tenant.infrastructure.logging;

import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that populates MDC (Mapped Diagnostic Context) for structured logging.
 *
 * <p>Per ADR-014 (Tail-Based Sampling) and observability best practices, every log line should
 * include:
 *
 * <ul>
 *   <li>trace_id / span_id - for distributed tracing correlation (set by OpenTelemetry)
 *   <li>tenant_id - for multi-tenant filtering
 *   <li>user_id - for user activity correlation
 *   <li>request_id - for request-level correlation
 * </ul>
 *
 * <p>This filter runs after TenantFilter (which populates TenantContext from JWT) and copies those
 * values to MDC so they appear in every log line within the request.
 *
 * @see TenantContext
 */
@Component
@Order(2)
public class MdcLoggingFilter extends OncePerRequestFilter {

  private static final String MDC_TENANT_ID = "tenant_id";
  private static final String MDC_USER_ID = "user_id";
  private static final String MDC_REQUEST_ID = "request_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String requestId = getOrGenerateRequestId(request);
      MDC.put(MDC_REQUEST_ID, requestId);

      UUID tenantId = TenantContext.getCurrentTenantId();
      if (tenantId != null) {
        MDC.put(MDC_TENANT_ID, tenantId.toString());
      }

      UUID userId = TenantContext.getCurrentUserId();
      if (userId != null) {
        MDC.put(MDC_USER_ID, userId.toString());
      }

      response.setHeader("X-Request-Id", requestId);

      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TENANT_ID);
      MDC.remove(MDC_USER_ID);
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  private String getOrGenerateRequestId(HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    return requestId;
  }
}
