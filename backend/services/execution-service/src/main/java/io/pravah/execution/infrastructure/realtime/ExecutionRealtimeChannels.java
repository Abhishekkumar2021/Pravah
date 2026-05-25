package io.pravah.execution.infrastructure.realtime;

import java.util.UUID;

/**
 * Redis pub/sub channel naming for execution UI fan-out (US-12.10).
 *
 * <p>Pattern {@code pravah:tenant:*:executions} is subscribed by each execution-service instance;
 * each message is JSON for browser WebSocket clients scoped to that tenant.
 */
public final class ExecutionRealtimeChannels {

  private static final String PREFIX = "pravah:tenant:";
  private static final String SUFFIX = ":executions";

  private ExecutionRealtimeChannels() {}

  public static String topic(UUID tenantId) {
    return PREFIX + tenantId + SUFFIX;
  }

  /** Parses tenant id from a channel name produced by {@link #topic(UUID)}. */
  public static UUID tenantFromTopic(String channel) {
    if (channel == null || !channel.startsWith(PREFIX) || !channel.endsWith(SUFFIX)) {
      throw new IllegalArgumentException("Unexpected channel: " + channel);
    }
    String mid = channel.substring(PREFIX.length(), channel.length() - SUFFIX.length());
    return UUID.fromString(mid);
  }
}
