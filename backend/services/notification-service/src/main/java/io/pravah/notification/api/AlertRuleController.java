package io.pravah.notification.api;

import io.pravah.notification.api.dto.AlertRuleResponse;
import io.pravah.notification.api.dto.CreateAlertRuleRequest;
import io.pravah.notification.api.dto.UpdateAlertRuleRequest;
import io.pravah.notification.application.AlertRuleApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

  private final AlertRuleApplicationService alertRuleService;

  public AlertRuleController(AlertRuleApplicationService alertRuleService) {
    this.alertRuleService = alertRuleService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public AlertRuleResponse create(@Valid @RequestBody CreateAlertRuleRequest request) {
    return alertRuleService.createAlertRule(request);
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public List<AlertRuleResponse> list(@RequestParam(required = false) UUID pipelineId) {
    return alertRuleService.listAlertRules(pipelineId);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public AlertRuleResponse get(@PathVariable UUID id) {
    return alertRuleService.getAlertRule(id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public AlertRuleResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateAlertRuleRequest request) {
    return alertRuleService.updateAlertRule(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public void delete(@PathVariable UUID id) {
    alertRuleService.deleteAlertRule(id);
  }

  @PatchMapping("/{id}/enable")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public AlertRuleResponse enable(@PathVariable UUID id) {
    return alertRuleService.toggleAlertRule(id, true);
  }

  @PatchMapping("/{id}/disable")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public AlertRuleResponse disable(@PathVariable UUID id) {
    return alertRuleService.toggleAlertRule(id, false);
  }
}
