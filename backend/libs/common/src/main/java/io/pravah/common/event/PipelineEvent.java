package io.pravah.common.event;

import io.pravah.common.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * Events related to pipeline lifecycle.
 *
 * @see <a href="../../../../../../docs/architecture/api-contracts.md">API Contracts - Kafka
 *     Events</a>
 */
public sealed interface PipelineEvent extends DomainEvent {

  UUID pipelineId();

  record PipelineCreated(
      UUID eventId,
      Instant occurredAt,
      TenantId tenantId,
      UUID pipelineId,
      String name,
      String createdBy)
      implements PipelineEvent {
    @Override
    public String eventType() {
      return "pipeline.created";
    }
  }

  record PipelineUpdated(
      UUID eventId,
      Instant occurredAt,
      TenantId tenantId,
      UUID pipelineId,
      int newVersion,
      String updatedBy)
      implements PipelineEvent {
    @Override
    public String eventType() {
      return "pipeline.updated";
    }
  }

  record PipelinePublished(
      UUID eventId, Instant occurredAt, TenantId tenantId, UUID pipelineId, int version)
      implements PipelineEvent {
    @Override
    public String eventType() {
      return "pipeline.published";
    }
  }

  record PipelineArchived(
      UUID eventId, Instant occurredAt, TenantId tenantId, UUID pipelineId, String archivedBy)
      implements PipelineEvent {
    @Override
    public String eventType() {
      return "pipeline.archived";
    }
  }

  record PipelineRestored(
      UUID eventId, Instant occurredAt, TenantId tenantId, UUID pipelineId, String restoredBy)
      implements PipelineEvent {
    @Override
    public String eventType() {
      return "pipeline.restored";
    }
  }
}
