package io.pravah.execution.api.internal;

import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.application.ExecutionApplicationService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/executions")
public class InternalScheduledExecutionController {

  private final ExecutionApplicationService executionApplicationService;

  public InternalScheduledExecutionController(
      ExecutionApplicationService executionApplicationService) {
    this.executionApplicationService = executionApplicationService;
  }

  @PostMapping("/scheduled")
  public ScheduledExecutionResponse startScheduled(@RequestBody ScheduledExecutionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    CreateExecutionResponse created =
        executionApplicationService.startScheduledExecution(
            tenantId,
            request.pipelineId(),
            request.scheduleId(),
            request.parameters() != null ? request.parameters() : java.util.Map.of());
    return new ScheduledExecutionResponse(created.id());
  }

  public record ScheduledExecutionRequest(
      @NotNull UUID pipelineId,
      @NotNull UUID scheduleId,
      java.util.Map<String, Object> parameters) {}

  public record ScheduledExecutionResponse(UUID executionId) {}
}
