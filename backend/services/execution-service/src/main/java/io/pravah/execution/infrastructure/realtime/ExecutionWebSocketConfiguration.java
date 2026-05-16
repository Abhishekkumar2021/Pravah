package io.pravah.execution.infrastructure.realtime;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Browser-facing execution updates over STOMP-less native WebSocket (US-12.10).
 *
 * <p>Each deployment must set {@code pravah.realtime.websocket.allowed-origins} (env {@code
 * PRAVAH_WS_ALLOWED_ORIGINS}) to every HTTPS origin that hosts the SPA; missing origins cause
 * failed handshakes with no application error body.
 */
@Configuration
@EnableWebSocket
public class ExecutionWebSocketConfiguration implements WebSocketConfigurer {

  private final ExecutionsWebSocketHandler executionsWebSocketHandler;
  private final JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor;

  @Value("${pravah.realtime.websocket.allowed-origins:http://localhost:5173}")
  private String allowedOrigins;

  public ExecutionWebSocketConfiguration(
      ExecutionsWebSocketHandler executionsWebSocketHandler,
      JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor) {
    this.executionsWebSocketHandler = executionsWebSocketHandler;
    this.jwtWebSocketHandshakeInterceptor = jwtWebSocketHandshakeInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    String[] origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    registry
        .addHandler(executionsWebSocketHandler, "/ws/v1/executions")
        .addInterceptors(jwtWebSocketHandshakeInterceptor)
        .setAllowedOrigins(origins);
  }
}
