package io.pravah.execution.infrastructure.realtime;

import java.util.UUID;

/** Published during a transaction; listeners run after successful commit (US-12.10). */
public record ExecutionRealtimeNotificationEvent(UUID tenantId, String jsonPayload) {}
