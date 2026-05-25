package io.pravah.scheduler.api.dto;

import io.pravah.scheduler.domain.model.Schedule;
import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
    UUID id,
    UUID pipelineId,
    String name,
    String cronExpression,
    String timezone,
    String catchupPolicy,
    boolean active,
    Instant nextRunAt,
    Instant lastRunAt,
    Instant createdAt) {

  public static ScheduleResponse from(Schedule schedule) {
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getPipelineId(),
        schedule.getName(),
        schedule.getCronExpression(),
        schedule.getTimezone(),
        schedule.getCatchupPolicy(),
        schedule.isActive(),
        schedule.getNextRunAt(),
        schedule.getLastRunAt(),
        schedule.getCreatedAt());
  }
}
