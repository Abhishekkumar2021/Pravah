package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.scheduler.api.dto.CreateScheduleRequest;
import io.pravah.scheduler.api.dto.CronPreviewRequest;
import io.pravah.scheduler.api.dto.CronPreviewResponse;
import io.pravah.scheduler.api.dto.ScheduleResponse;
import io.pravah.scheduler.domain.model.Schedule;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScheduleService {

  private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

  private final ScheduleRepository scheduleRepository;

  public ScheduleService(ScheduleRepository scheduleRepository) {
    this.scheduleRepository = scheduleRepository;
  }

  public ScheduleResponse createSchedule(CreateScheduleRequest request) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    // TODO(US-03.04): Validate pipelineId exists via pipeline-service internal API
    // For now, evaluation job fails gracefully if pipeline doesn't exist

    validateCron(request.cronExpression(), request.timezone());

    String catchupPolicy =
        request.catchupPolicy() != null && !request.catchupPolicy().isBlank()
            ? request.catchupPolicy().trim()
            : "skip";
    if (!catchupPolicy.equals("skip")
        && !catchupPolicy.equals("run_all")
        && !catchupPolicy.equals("coalesce")) {
      throw ValidationException.of("catchupPolicy", "Must be 'skip', 'run_all', or 'coalesce'");
    }

    Instant now = Instant.now();
    Instant firstRun =
        CronScheduleCalculator.nextRunAfter(request.cronExpression(), request.timezone(), now);

    Schedule schedule =
        Schedule.builder()
            .tenantId(tenantId)
            .pipelineId(request.pipelineId())
            .name(request.name().trim())
            .cronExpression(request.cronExpression().trim())
            .timezone(request.timezone().trim())
            .catchupPolicy(catchupPolicy)
            .nextRunAt(firstRun)
            .createdBy(userId)
            .build();

    schedule = scheduleRepository.save(schedule);
    log.info(
        "Created schedule",
        kv("schedule_id", schedule.getId()),
        kv("pipeline_id", schedule.getPipelineId()),
        kv("next_run_at", firstRun));
    return ScheduleResponse.from(schedule);
  }

  @Transactional(readOnly = true)
  public List<ScheduleResponse> listSchedules(UUID pipelineId) {
    UUID tenantId = requireTenantId();
    return scheduleRepository
        .findByTenantIdAndPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId)
        .stream()
        .map(ScheduleResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ScheduleResponse getSchedule(UUID scheduleId) {
    return ScheduleResponse.from(findScheduleOrThrow(scheduleId));
  }

  public ScheduleResponse pauseSchedule(UUID scheduleId) {
    Schedule schedule = findScheduleOrThrow(scheduleId);
    if (!schedule.isActive()) {
      throw ValidationException.of("scheduleId", "Schedule is already paused");
    }
    schedule.pause();
    schedule = scheduleRepository.save(schedule);
    log.info("Paused schedule", kv("schedule_id", scheduleId));
    return ScheduleResponse.from(schedule);
  }

  public ScheduleResponse resumeSchedule(UUID scheduleId) {
    Schedule schedule = findScheduleOrThrow(scheduleId);
    if (schedule.isActive()) {
      throw ValidationException.of("scheduleId", "Schedule is already active");
    }
    Instant next =
        CronScheduleCalculator.nextRunAfter(
            schedule.getCronExpression(), schedule.getTimezone(), Instant.now());
    schedule.resume(next);
    schedule = scheduleRepository.save(schedule);
    log.info("Resumed schedule", kv("schedule_id", scheduleId), kv("next_run_at", next));
    return ScheduleResponse.from(schedule);
  }

  public void deleteSchedule(UUID scheduleId) {
    Schedule schedule = findScheduleOrThrow(scheduleId);
    scheduleRepository.delete(schedule);
    log.info("Deleted schedule", kv("schedule_id", scheduleId));
  }

  @Transactional(readOnly = true)
  public CronPreviewResponse previewCron(CronPreviewRequest request) {
    validateCron(request.cronExpression(), request.timezone());
    Instant after = request.after() != null ? request.after() : Instant.now();
    int count = request.count() != null ? request.count() : 10;
    if (count > 10) {
      count = 10;
    }
    String description = CronScheduleCalculator.describe(request.cronExpression(), Locale.ENGLISH);
    List<Instant> nextRuns =
        CronScheduleCalculator.nextRuns(request.cronExpression(), request.timezone(), after, count);
    return new CronPreviewResponse(description, nextRuns);
  }

  private Schedule findScheduleOrThrow(UUID scheduleId) {
    UUID tenantId = requireTenantId();
    return scheduleRepository
        .findByIdAndTenantId(scheduleId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Schedule", scheduleId));
  }

  private static void validateCron(String cronExpression, String timezone) {
    try {
      CronScheduleCalculator.parse(cronExpression);
      CronScheduleCalculator.zoneId(timezone);
    } catch (IllegalArgumentException e) {
      throw ValidationException.of("cronExpression", e.getMessage());
    }
  }

  private static UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }

  private static UUID requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No user context set");
    }
    return userId;
  }
}
