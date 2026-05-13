package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Events related to tenant management.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka Events</a>
 */
public sealed interface TenantEvent extends DomainEvent {

    record TenantCreated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String name,
        String plan
    ) implements TenantEvent {
        @Override
        public String eventType() {
            return "tenant.created";
        }
    }

    record TenantUpdated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String name
    ) implements TenantEvent {
        @Override
        public String eventType() {
            return "tenant.updated";
        }
    }

    record TenantSuspended(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String reason
    ) implements TenantEvent {
        @Override
        public String eventType() {
            return "tenant.suspended";
        }
    }

    record TenantActivated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId
    ) implements TenantEvent {
        @Override
        public String eventType() {
            return "tenant.activated";
        }
    }

    record TenantDeleted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId
    ) implements TenantEvent {
        @Override
        public String eventType() {
            return "tenant.deleted";
        }
    }
}
