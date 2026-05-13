package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Events related to pipeline run lifecycle.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka Events</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Run States</a>
 */
public sealed interface RunEvent extends DomainEvent {

    UUID runId();
    UUID pipelineId();

    record RunTriggered(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId,
        int pipelineVersion,
        String triggeredBy,
        String triggerType
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.triggered";
        }
    }

    record RunStarted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.started";
        }
    }

    record RunSucceeded(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId,
        long durationMs
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.succeeded";
        }
    }

    record RunFailed(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId,
        String failureReason
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.failed";
        }
    }

    record RunCancelled(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId,
        String cancelledBy
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.cancelled";
        }
    }

    record RunRetrying(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID runId,
        UUID pipelineId,
        int attemptNumber
    ) implements RunEvent {
        @Override
        public String eventType() {
            return "run.retrying";
        }
    }
}
