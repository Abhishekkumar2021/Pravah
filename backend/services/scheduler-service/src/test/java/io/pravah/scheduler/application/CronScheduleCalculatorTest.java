package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CronScheduleCalculatorTest {

  @Test
  void nextRuns_dailyAt9Utc() {
    Instant after = Instant.parse("2026-05-16T08:00:00Z");
    List<Instant> runs = CronScheduleCalculator.nextRuns("0 9 * * *", "UTC", after, 3);
    assertThat(runs).hasSize(3);
    assertThat(runs.get(0)).isEqualTo(Instant.parse("2026-05-16T09:00:00Z"));
    assertThat(runs.get(1)).isEqualTo(Instant.parse("2026-05-17T09:00:00Z"));
  }

  @Test
  void describe_returnsHumanReadableText() {
    String text = CronScheduleCalculator.describe("0 9 * * *", java.util.Locale.ENGLISH);
    assertThat(text).isNotBlank();
  }

  @Test
  void parse_rejectsInvalidFieldCount() {
    assertThatThrownBy(() -> CronScheduleCalculator.parse("* * *"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
