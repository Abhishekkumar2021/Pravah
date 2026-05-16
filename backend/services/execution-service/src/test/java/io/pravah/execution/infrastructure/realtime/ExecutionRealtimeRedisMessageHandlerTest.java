package io.pravah.execution.infrastructure.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

@ExtendWith(MockitoExtension.class)
class ExecutionRealtimeRedisMessageHandlerTest {

  @Mock private ExecutionWebSocketSessionRegistry registry;

  private ExecutionRealtimeRedisMessageHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ExecutionRealtimeRedisMessageHandler(registry);
  }

  @Test
  void onRedisMessage_validChannel_broadcasts() {
    UUID tenantId = UUID.randomUUID();
    String channel = ExecutionRealtimeChannels.topic(tenantId);
    String json = "{\"type\":\"execution.updated\"}";
    Message msg =
        new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));
    handler.onRedisMessage(msg, null);
    verify(registry).broadcast(eq(tenantId), eq(json));
  }

  @Test
  void onRedisMessage_malformedChannel_doesNotBroadcast() {
    Message msg =
        new DefaultMessage(
            "not-a-valid-channel".getBytes(StandardCharsets.UTF_8),
            "{}".getBytes(StandardCharsets.UTF_8));
    handler.onRedisMessage(msg, null);
    verify(registry, never()).broadcast(any(), any());
  }
}
