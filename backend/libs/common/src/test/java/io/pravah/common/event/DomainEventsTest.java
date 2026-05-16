package io.pravah.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.domain.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEventsTest {

  private static final TenantId TENANT = TenantId.of("dev-local");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID PIPELINE_ID = UUID.randomUUID();
  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID RUNNER_ID = UUID.randomUUID();

  @Test
  void allPipelineEvents_exposeEventTypes() {
    assertThat(
            new PipelineEvent.PipelineCreated(
                    EVENT_ID, NOW, TENANT, PIPELINE_ID, "ETL", "user-1")
                .eventType())
        .isEqualTo("pipeline.created");
    assertThat(
            new PipelineEvent.PipelineUpdated(EVENT_ID, NOW, TENANT, PIPELINE_ID, 2, "user-1")
                .eventType())
        .isEqualTo("pipeline.updated");
    assertThat(
            new PipelineEvent.PipelinePublished(EVENT_ID, NOW, TENANT, PIPELINE_ID, 1)
                .eventType())
        .isEqualTo("pipeline.published");
    assertThat(
            new PipelineEvent.PipelineArchived(EVENT_ID, NOW, TENANT, PIPELINE_ID, "user-1")
                .eventType())
        .isEqualTo("pipeline.archived");
    assertThat(
            new PipelineEvent.PipelineRestored(EVENT_ID, NOW, TENANT, PIPELINE_ID, "user-1")
                .eventType())
        .isEqualTo("pipeline.restored");
  }

  @Test
  void allRunEvents_exposeEventTypes() {
    assertThat(
            new RunEvent.RunTriggered(
                    EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID, 1, "user-1", "manual")
                .eventType())
        .isEqualTo("run.triggered");
    assertThat(new RunEvent.RunStarted(EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID).eventType())
        .isEqualTo("run.started");
    assertThat(
            new RunEvent.RunSucceeded(EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID, 1000L)
                .eventType())
        .isEqualTo("run.succeeded");
    assertThat(
            new RunEvent.RunFailed(EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID, "boom").eventType())
        .isEqualTo("run.failed");
    assertThat(
            new RunEvent.RunCancelled(EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID, "user-1")
                .eventType())
        .isEqualTo("run.cancelled");
    assertThat(
            new RunEvent.RunRetrying(EVENT_ID, NOW, TENANT, RUN_ID, PIPELINE_ID, 2).eventType())
        .isEqualTo("run.retrying");
  }

  @Test
  void allJobEvents_exposeEventTypes() {
    assertThat(
            new JobEvent.JobPending(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, "build").eventType())
        .isEqualTo("job.pending");
    assertThat(new JobEvent.JobQueued(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID).eventType())
        .isEqualTo("job.queued");
    assertThat(
            new JobEvent.JobAssigned(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, RUNNER_ID).eventType())
        .isEqualTo("job.assigned");
    assertThat(
            new JobEvent.JobStarted(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, RUNNER_ID).eventType())
        .isEqualTo("job.started");
    assertThat(
            new JobEvent.JobSucceeded(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, 500L).eventType())
        .isEqualTo("job.succeeded");
    assertThat(
            new JobEvent.JobFailed(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, "err", 1).eventType())
        .isEqualTo("job.failed");
    assertThat(
            new JobEvent.JobCancelled(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, "user-1")
                .eventType())
        .isEqualTo("job.cancelled");
    assertThat(
            new JobEvent.JobTimedOut(EVENT_ID, NOW, TENANT, JOB_ID, RUN_ID, 60L).eventType())
        .isEqualTo("job.timedout");
  }

  @Test
  void allTenantEvents_exposeEventTypes() {
    assertThat(new TenantEvent.TenantCreated(EVENT_ID, NOW, TENANT, "Acme", "team").eventType())
        .isEqualTo("tenant.created");
    assertThat(new TenantEvent.TenantUpdated(EVENT_ID, NOW, TENANT, "Acme Inc").eventType())
        .isEqualTo("tenant.updated");
    assertThat(new TenantEvent.TenantSuspended(EVENT_ID, NOW, TENANT, "billing").eventType())
        .isEqualTo("tenant.suspended");
    assertThat(new TenantEvent.TenantActivated(EVENT_ID, NOW, TENANT).eventType())
        .isEqualTo("tenant.activated");
    assertThat(new TenantEvent.TenantDeleted(EVENT_ID, NOW, TENANT).eventType())
        .isEqualTo("tenant.deleted");
  }

  @Test
  void allRunnerEvents_exposeEventTypes() {
    assertThat(
            new RunnerEvent.RunnerRegistered(
                    EVENT_ID, NOW, TENANT, RUNNER_ID, "runner-1", "1.0.0")
                .eventType())
        .isEqualTo("runner.registered");
    assertThat(new RunnerEvent.RunnerOnline(EVENT_ID, NOW, TENANT, RUNNER_ID).eventType())
        .isEqualTo("runner.online");
    assertThat(new RunnerEvent.RunnerBusy(EVENT_ID, NOW, TENANT, RUNNER_ID, 2).eventType())
        .isEqualTo("runner.busy");
    assertThat(new RunnerEvent.RunnerDraining(EVENT_ID, NOW, TENANT, RUNNER_ID).eventType())
        .isEqualTo("runner.draining");
    assertThat(
            new RunnerEvent.RunnerOffline(EVENT_ID, NOW, TENANT, RUNNER_ID, "shutdown").eventType())
        .isEqualTo("runner.offline");
    assertThat(new RunnerEvent.RunnerDeregistered(EVENT_ID, NOW, TENANT, RUNNER_ID).eventType())
        .isEqualTo("runner.deregistered");
  }

  @Test
  void notificationRequested_exposesEventType() {
    assertThat(
            new NotificationEvent.NotificationRequested(
                    EVENT_ID, NOW, TENANT, "email", "u@example.com", "welcome", "{}")
                .eventType())
        .isEqualTo("notification.requested");
  }

  @Test
  void domainEvents_shareSchemaVersion() {
    assertThat(
            new PipelineEvent.PipelineCreated(
                    EVENT_ID, NOW, TENANT, PIPELINE_ID, "ETL", "user-1")
                .schemaVersion())
        .isEqualTo(1);
  }
}
