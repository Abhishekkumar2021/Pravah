package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleCoalesceParametersTest {

  private static final UUID SCHEDULE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

  @Test
  void toExecutionParameters_includesCombinedInterval() {
    Instant first = Instant.parse("2026-05-14T09:00:00Z");
    Instant last = Instant.parse("2026-05-16T09:00:00Z");

    Map<String, Object> params =
        ScheduleCoalesceParameters.toExecutionParameters(SCHEDULE_ID, first, last, 3);

    @SuppressWarnings("unchecked")
    Map<String, Object> coalesce =
        (Map<String, Object>) ((Map<String, Object>) params.get("_trigger")).get("coalesce");
    assertThat(coalesce.get("scheduleId")).isEqualTo(SCHEDULE_ID.toString());
    assertThat(coalesce.get("firstScheduledAt")).isEqualTo(first.toString());
    assertThat(coalesce.get("lastScheduledAt")).isEqualTo(last.toString());
    assertThat(coalesce.get("missedSlotCount")).isEqualTo(3);
  }

  @Test
  void exceedsMaxInterval_whenSpanTooLarge() {
    Instant first = Instant.parse("2026-01-01T00:00:00Z");
    Instant last = Instant.parse("2026-02-01T00:00:00Z");

    assertThat(ScheduleCoalesceParameters.exceedsMaxInterval(first, last, Duration.ofDays(7)))
        .isTrue();
    assertThat(ScheduleCoalesceParameters.exceedsMaxInterval(first, last, Duration.ofDays(60)))
        .isFalse();
  }
}
