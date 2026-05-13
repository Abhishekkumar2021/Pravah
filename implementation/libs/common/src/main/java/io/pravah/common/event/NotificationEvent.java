package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Events for the notification service to consume.
 * These are essentially "notification requests" published by other services.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka Events</a>
 */
public sealed interface NotificationEvent extends DomainEvent {

    record NotificationRequested(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String channel,
        String recipient,
        String templateId,
        String payload
    ) implements NotificationEvent {
        @Override
        public String eventType() {
            return "notification.requested";
        }
    }
}
