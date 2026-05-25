package io.pravah.runnerservice.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.runnerservice.service.RunnerService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates runner agents on job-scoped REST endpoints using the stream token issued at
 * registration (ADR-005). Replaces shared internal-secret auth for secret resolution.
 */
@Order(0)
public class RunnerAgentAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RunnerAgentAuthFilter.class);

  static final String RUNNER_AGENT_PATH =
      "^/api/v1/runners/([0-9a-fA-F\\-]{36})/jobs/([0-9a-fA-F\\-]{36})/environment-secrets$";

  private static final Pattern PATH_PATTERN = Pattern.compile(RUNNER_AGENT_PATH);

  private final RunnerService runnerService;

  public RunnerAgentAuthFilter(RunnerService runnerService) {
    this.runnerService = runnerService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return path == null || !PATH_PATTERN.matcher(path).matches();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Matcher matcher = PATH_PATTERN.matcher(request.getRequestURI());
    if (!matcher.matches()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    UUID runnerId;
    try {
      runnerId = UUID.fromString(matcher.group(1));
    } catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid runner id");
      return;
    }
    String token = extractBearerToken(request.getHeader("Authorization"));
    if (token == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing runner token");
      return;
    }
    var runner = runnerService.validateToken(runnerId, token);
    if (runner.isEmpty()) {
      log.warn("Invalid runner token for secret resolution", kv("runner_id", runnerId));
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid runner token");
      return;
    }
    UUID tenantId = runner.get().getTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      request.setAttribute(RunnerAgentRequestAttributes.RUNNER_ID, runnerId);
      var authentication =
          new UsernamePasswordAuthenticationToken("runner:" + runnerId, null, java.util.List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private static String extractBearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return null;
    }
    String token = authorization.substring("Bearer ".length()).trim();
    return token.isEmpty() ? null : token;
  }
}
