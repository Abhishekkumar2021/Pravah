package io.pravah.execution.infrastructure.realtime;

import java.util.UUID;

/** Delivers ephemeral execution updates to WebSocket subscribers for a tenant. */
public interface ExecutionRealtimeFanout {

  void publish(UUID tenantId, String jsonPayload);
}
