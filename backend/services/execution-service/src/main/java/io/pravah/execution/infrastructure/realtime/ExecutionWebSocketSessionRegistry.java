package io.pravah.execution.infrastructure.realtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ExecutionWebSocketSessionRegistry {

  private static final Logger log =
      LoggerFactory.getLogger(ExecutionWebSocketSessionRegistry.class);

  private final ConcurrentHashMap<UUID, Set<WebSocketSession>> sessionsByTenant =
      new ConcurrentHashMap<>();

  /**
   * Holds all open WebSocket sessions authenticated for a tenant. {@link #broadcast} delivers every
   * execution update for that tenant to each session; browser clients filter by execution id.
   */
  public void register(UUID tenantId, WebSocketSession session) {
    sessionsByTenant.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(session);
    log.debug("WebSocket registered for tenant {}", tenantId);
  }

  public void remove(UUID tenantId, WebSocketSession session) {
    Set<WebSocketSession> set = sessionsByTenant.get(tenantId);
    if (set != null) {
      set.remove(session);
      if (set.isEmpty()) {
        sessionsByTenant.remove(tenantId, set);
      }
    }
  }

  public void broadcast(UUID tenantId, String jsonPayload) {
    Set<WebSocketSession> set = sessionsByTenant.get(tenantId);
    if (set == null || set.isEmpty()) {
      return;
    }
    TextMessage message = new TextMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
    for (WebSocketSession s : Set.copyOf(set)) {
      if (!s.isOpen()) {
        set.remove(s);
        continue;
      }
      try {
        synchronized (s) {
          s.sendMessage(message);
        }
      } catch (IOException e) {
        log.warn("Failed to push WebSocket message", e);
        try {
          s.close();
        } catch (IOException closeEx) {
          log.debug("Failed to close WebSocket session after send failure", closeEx);
        }
        set.remove(s);
      }
    }
  }
}
