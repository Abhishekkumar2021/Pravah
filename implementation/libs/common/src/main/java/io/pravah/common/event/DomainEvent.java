package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events in Pravah.
 * <p>
 * Domain events represent something that happened in the domain.
 * They are immutable and should contain all information needed
 * to understand what happened.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Domain Events</a>
 * @see <a href="../../../../../../docs/adr/ADR-003-outbox-pattern.md">ADR-003: Outbox Pattern</a>
 */
public sealed interface DomainEvent permits
    PipelineEvent,
    RunEvent,
    JobEvent,
    TenantEvent,
    RunnerEvent,
    NotificationEvent {

    /**
     * Unique identifier for this event instance.
     * Used for idempotency and deduplication.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * Type identifier for routing and deserialization.
     * Format: {aggregate}.{action} (e.g., "pipeline.created", "run.started")
     *
     * @return the event type string
     */
    String eventType();

    /**
     * When this event occurred.
     *
     * @return the event timestamp
     */
    Instant occurredAt();

    /**
     * The tenant this event belongs to.
     *
     * @return the tenant ID
     */
    TenantId tenantId();

    /**
     * Version of the event schema for compatibility.
     *
     * @return the schema version
     */
    default int schemaVersion() {
        return 1;
    }
}
