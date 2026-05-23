package io.pravah.scheduler.api.rest;

import io.pravah.scheduler.api.dto.CreatePipelineTriggerRequest;
import io.pravah.scheduler.api.dto.PipelineTriggerResponse;
import io.pravah.scheduler.api.dto.UpdatePipelineTriggerRequest;
import io.pravah.scheduler.application.PipelineTriggerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD for pipeline event triggers (US-03.06, US-03.07). */
@RestController
@RequestMapping("/api/v1/triggers")
public class PipelineTriggerController {

  private final PipelineTriggerService triggerService;

  public PipelineTriggerController(PipelineTriggerService triggerService) {
    this.triggerService = triggerService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineTriggerResponse create(@Valid @RequestBody CreatePipelineTriggerRequest request) {
    return triggerService.createTrigger(request);
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public List<PipelineTriggerResponse> list(@RequestParam UUID pipelineId) {
    return triggerService.listTriggers(pipelineId);
  }

  @GetMapping("/{triggerId}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public PipelineTriggerResponse get(@PathVariable UUID triggerId) {
    return triggerService.getTrigger(triggerId);
  }

  @PutMapping("/{triggerId}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineTriggerResponse update(
      @PathVariable UUID triggerId, @Valid @RequestBody UpdatePipelineTriggerRequest request) {
    return triggerService.updateTrigger(triggerId, request);
  }

  @PostMapping("/{triggerId}/disable")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineTriggerResponse disable(@PathVariable UUID triggerId) {
    return triggerService.disableTrigger(triggerId);
  }

  @PostMapping("/{triggerId}/enable")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineTriggerResponse enable(@PathVariable UUID triggerId) {
    return triggerService.enableTrigger(triggerId);
  }

  @DeleteMapping("/{triggerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public void delete(@PathVariable UUID triggerId) {
    triggerService.deleteTrigger(triggerId);
  }
}
