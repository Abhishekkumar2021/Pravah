package io.pravah.execution.infrastructure.realtime;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.stereotype.Component;

/**
 * Dispatches Redis pub/sub payloads to in-memory WebSocket sessions for the parsed tenant.
 *
 * <p>Malformed channel names are ignored so a bad publish cannot take down the listener container.
 */
@Component
@ConditionalOnProperty(
    prefix = "pravah.realtime",
    name = "redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExecutionRealtimeRedisMessageHandler {

  private static final Logger log =
      LoggerFactory.getLogger(ExecutionRealtimeRedisMessageHandler.class);

  private final ExecutionWebSocketSessionRegistry registry;

  public ExecutionRealtimeRedisMessageHandler(ExecutionWebSocketSessionRegistry registry) {
    this.registry = registry;
  }

  /**
   * Handles one message from Redis pattern {@code pravah:tenant:*:executions}. Malformed channels
   * are logged and skipped.
   */
  public void onRedisMessage(Message message, byte[] pattern) {
    String channel =
        new String(Objects.requireNonNull(message.getChannel()), StandardCharsets.UTF_8);
    String body = new String(Objects.requireNonNull(message.getBody()), StandardCharsets.UTF_8);
    try {
      registry.broadcast(ExecutionRealtimeChannels.tenantFromTopic(channel), body);
    } catch (IllegalArgumentException e) {
      log.warn("Ignoring realtime Redis message on unexpected channel {}", channel, e);
    }
  }
}
