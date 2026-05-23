package io.pravah.scheduler.api.rest;

import io.pravah.scheduler.api.dto.CreateScheduleRequest;
import io.pravah.scheduler.api.dto.CronPreviewRequest;
import io.pravah.scheduler.api.dto.CronPreviewResponse;
import io.pravah.scheduler.api.dto.ScheduleResponse;
import io.pravah.scheduler.application.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for pipeline cron schedules (US-03.01). */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

  private final ScheduleService scheduleService;

  public ScheduleController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public ScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
    return scheduleService.createSchedule(request);
  }

  @PostMapping("/preview")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:write', 'pipelines:*')")
  public CronPreviewResponse preview(@Valid @RequestBody CronPreviewRequest request) {
    return scheduleService.previewCron(request);
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public List<ScheduleResponse> list(@RequestParam UUID pipelineId) {
    return scheduleService.listSchedules(pipelineId);
  }

  @GetMapping("/{scheduleId}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public ScheduleResponse get(@PathVariable UUID scheduleId) {
    return scheduleService.getSchedule(scheduleId);
  }

  @PostMapping("/{scheduleId}/pause")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public ScheduleResponse pause(@PathVariable UUID scheduleId) {
    return scheduleService.pauseSchedule(scheduleId);
  }

  @PostMapping("/{scheduleId}/resume")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public ScheduleResponse resume(@PathVariable UUID scheduleId) {
    return scheduleService.resumeSchedule(scheduleId);
  }

  @DeleteMapping("/{scheduleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public ResponseEntity<Void> delete(@PathVariable UUID scheduleId) {
    scheduleService.deleteSchedule(scheduleId);
    return ResponseEntity.noContent().build();
  }
}
