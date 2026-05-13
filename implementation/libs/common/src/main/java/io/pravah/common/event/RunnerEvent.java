package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Events related to runner lifecycle.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka Events</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Runner States</a>
 */
public sealed interface RunnerEvent extends DomainEvent {

    UUID runnerId();

    record RunnerRegistered(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId,
        String name,
        String version
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.registered";
        }
    }

    record RunnerOnline(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.online";
        }
    }

    record RunnerBusy(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId,
        int activeJobs
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.busy";
        }
    }

    record RunnerDraining(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.draining";
        }
    }

    record RunnerOffline(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId,
        String reason
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.offline";
        }
    }

    record RunnerDeregistered(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runnerId
    ) implements RunnerEvent {
        @Override
        public String eventType() {
            return "runner.deregistered";
        }
    }
}
