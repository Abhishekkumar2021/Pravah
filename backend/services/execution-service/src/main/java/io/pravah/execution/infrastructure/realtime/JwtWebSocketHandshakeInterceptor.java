package io.pravah.execution.infrastructure.realtime;

import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Validates the JWT during the WebSocket HTTP upgrade (US-12.10).
 *
 * <p>Accepts the bearer token from the {@code access_token} query parameter (required for browser
 * {@code WebSocket} because custom headers are not portable) or from the {@code Authorization}
 * header. Operators should avoid logging raw request lines or query strings at INFO in production,
 * since the query form can expose short-lived credentials to access logs.
 *
 * <p>On failure, {@link #beforeHandshake} returns {@code false} (handshake rejected); no token
 * value is written to logs.
 */
@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

  private static final Logger log = LoggerFactory.getLogger(JwtWebSocketHandshakeInterceptor.class);

  public static final String ATTR_TENANT_ID = "pravah.tenantId";
  public static final String ATTR_USER_ID = "pravah.userId";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenVerifier jwtTokenVerifier;

  public JwtWebSocketHandshakeInterceptor(JwtTokenVerifier jwtTokenVerifier) {
    this.jwtTokenVerifier = jwtTokenVerifier;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String token = resolveBearerToken(request);
    if (token == null || token.isBlank()) {
      log.debug("WebSocket handshake rejected: missing bearer token or access_token query");
      return false;
    }
    try {
      var claims = jwtTokenVerifier.validateAndGetClaims(token);
      UUID tenantId = claims.tenantId();
      UUID userId = claims.userId();
      attributes.put(ATTR_TENANT_ID, tenantId);
      attributes.put(ATTR_USER_ID, userId);
      return true;
    } catch (JwtVerificationException e) {
      log.debug("WebSocket handshake rejected: JWT validation failed", e);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }

  private static String resolveBearerToken(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servlet) {
      var req = servlet.getServletRequest();
      String fromQuery = req.getParameter("access_token");
      if (fromQuery != null && !fromQuery.isBlank()) {
        return fromQuery.trim();
      }
      String auth = req.getHeader("Authorization");
      if (auth != null
          && auth.length() > 7
          && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
        return auth.substring(BEARER_PREFIX.length()).trim();
      }
    }
    return null;
  }
}
