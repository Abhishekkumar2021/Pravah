package io.pravah.scheduler.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Execution parameters for coalesced schedule catchup (US-03.15). */
public final class ScheduleCoalesceParameters {

  private ScheduleCoalesceParameters() {}

  public static Map<String, Object> toExecutionParameters(
      UUID scheduleId, Instant firstScheduledAt, Instant lastScheduledAt, int missedSlotCount) {
    Map<String, Object> coalesce = new LinkedHashMap<>();
    coalesce.put("scheduleId", scheduleId.toString());
    coalesce.put("firstScheduledAt", firstScheduledAt.toString());
    coalesce.put("lastScheduledAt", lastScheduledAt.toString());
    coalesce.put("missedSlotCount", missedSlotCount);
    return Map.of("_trigger", Map.of("coalesce", Map.copyOf(coalesce)));
  }

  public static boolean exceedsMaxInterval(
      Instant firstScheduledAt, Instant lastScheduledAt, java.time.Duration maxInterval) {
    if (maxInterval == null || maxInterval.isZero() || maxInterval.isNegative()) {
      return false;
    }
    return java.time.Duration.between(firstScheduledAt, lastScheduledAt).compareTo(maxInterval) > 0;
  }
}
