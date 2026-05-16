package io.pravah.scheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleHistoryTest {

  @Test
  void triggered_setsExecutionIdAndStatus() {
    UUID tenantId = UUID.randomUUID();
    UUID scheduleId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    Instant scheduledTime = Instant.parse("2026-05-16T09:00:00Z");

    ScheduleHistory history =
        ScheduleHistory.triggered(tenantId, scheduleId, scheduledTime, executionId);

    assertThat(history.getTenantId()).isEqualTo(tenantId);
    assertThat(history.getScheduleId()).isEqualTo(scheduleId);
    assertThat(history.getScheduledTime()).isEqualTo(scheduledTime);
    assertThat(history.getExecutionId()).isEqualTo(executionId);
    assertThat(history.getStatus()).isEqualTo(ScheduleHistory.STATUS_TRIGGERED);
    assertThat(history.getCreatedAt()).isNotNull();
  }

  @Test
  void failed_hasNoExecutionId() {
    ScheduleHistory history =
        ScheduleHistory.failed(
            UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-05-16T09:00:00Z"));

    assertThat(history.getExecutionId()).isNull();
    assertThat(history.getStatus()).isEqualTo(ScheduleHistory.STATUS_FAILED);
  }
}
