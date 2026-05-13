package io.pravah.common.event;

import io.pravah.common.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Events related to job lifecycle within a pipeline run.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka Events</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Job States</a>
 */
public sealed interface JobEvent extends DomainEvent {

    UUID jobId();
    UUID runId();

    record JobPending(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        String jobName
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.pending";
        }
    }

    record JobQueued(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.queued";
        }
    }

    record JobAssigned(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        UUID runnerId
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.assigned";
        }
    }

    record JobStarted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        UUID runnerId
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.started";
        }
    }

    record JobSucceeded(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        long durationMs
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.succeeded";
        }
    }

    record JobFailed(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        String failureReason,
        int exitCode
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.failed";
        }
    }

    record JobCancelled(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        String cancelledBy
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.cancelled";
        }
    }

    record JobTimedOut(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID jobId,
        UUID runId,
        long timeoutSeconds
    ) implements JobEvent {
        @Override
        public String eventType() {
            return "job.timedout";
        }
    }
}
