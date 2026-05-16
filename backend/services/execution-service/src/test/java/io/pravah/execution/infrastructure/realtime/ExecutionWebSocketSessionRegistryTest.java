package io.pravah.execution.infrastructure.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class ExecutionWebSocketSessionRegistryTest {

  @Test
  void broadcast_openSession_sendsPayload() throws Exception {
    ExecutionWebSocketSessionRegistry registry = new ExecutionWebSocketSessionRegistry();
    UUID tenantId = UUID.randomUUID();
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);

    registry.register(tenantId, session);
    registry.broadcast(tenantId, "{\"type\":\"execution.updated\"}");

    verify(session).sendMessage(any(TextMessage.class));
  }

  @Test
  void broadcast_unknownTenant_doesNotSend() throws Exception {
    ExecutionWebSocketSessionRegistry registry = new ExecutionWebSocketSessionRegistry();
    WebSocketSession session = mock(WebSocketSession.class);
    registry.broadcast(UUID.randomUUID(), "{}");
    verify(session, never()).sendMessage(any());
  }
}
