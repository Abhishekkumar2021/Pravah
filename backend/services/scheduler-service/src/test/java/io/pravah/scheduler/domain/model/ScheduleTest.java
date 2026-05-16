package io.pravah.scheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleTest {

  @Test
  void builder_defaultsParametersAndCatchupPolicy() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Schedule schedule =
        Schedule.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .name("Nightly")
            .cronExpression("0 2 * * *")
            .createdBy(userId)
            .build();

    assertThat(schedule.getParameters()).isEqualTo("{}");
    assertThat(schedule.getCatchupPolicy()).isEqualTo("skip");
    assertThat(schedule.getTimezone()).isEqualTo("UTC");
    assertThat(schedule.isActive()).isTrue();
    assertThat(schedule.getId()).isNotNull();
  }

  @Test
  void pauseAndResume_updateActiveAndNextRun() {
    Instant next = Instant.parse("2026-05-20T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .name("Job")
            .cronExpression("0 9 * * *")
            .createdBy(UUID.randomUUID())
            .build();

    schedule.pause();
    assertThat(schedule.isActive()).isFalse();

    schedule.resume(next);
    assertThat(schedule.isActive()).isTrue();
    assertThat(schedule.getNextRunAt()).isEqualTo(next);
  }

  @Test
  void recordTriggeredRun_updatesLastAndNextRun() {
    Instant triggered = Instant.parse("2026-05-16T09:00:00Z");
    Instant next = Instant.parse("2026-05-17T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .name("Job")
            .cronExpression("0 9 * * *")
            .createdBy(UUID.randomUUID())
            .build();

    schedule.recordTriggeredRun(triggered, next);

    assertThat(schedule.getLastRunAt()).isEqualTo(triggered);
    assertThat(schedule.getNextRunAt()).isEqualTo(next);
  }
}
