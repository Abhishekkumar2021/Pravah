package io.pravah.execution.infrastructure.realtime;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ExecutionsWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ExecutionsWebSocketHandler.class);

  private final ExecutionWebSocketSessionRegistry registry;

  public ExecutionsWebSocketHandler(ExecutionWebSocketSessionRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Object tenant = session.getAttributes().get(JwtWebSocketHandshakeInterceptor.ATTR_TENANT_ID);
    if (!(tenant instanceof UUID tenantId)) {
      log.warn("WebSocket missing tenant attribute; closing");
      try {
        session.close(CloseStatus.NOT_ACCEPTABLE);
      } catch (IOException e) {
        log.debug("Failed to close WebSocket after missing tenant attribute", e);
      }
      return;
    }
    registry.register(tenantId, session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Object tenant = session.getAttributes().get(JwtWebSocketHandshakeInterceptor.ATTR_TENANT_ID);
    if (tenant instanceof UUID tenantId) {
      registry.remove(tenantId, session);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // Client may send pings later (US-12.09). Proxies/LBs should allow long-lived upgrades;
    // configure idle timeouts above expected run duration.
  }
}
