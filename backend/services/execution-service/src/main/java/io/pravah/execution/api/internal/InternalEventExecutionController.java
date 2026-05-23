package io.pravah.execution.api.internal;

import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.application.ExecutionApplicationService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/executions")
public class InternalEventExecutionController {

  private final ExecutionApplicationService executionApplicationService;

  public InternalEventExecutionController(ExecutionApplicationService executionApplicationService) {
    this.executionApplicationService = executionApplicationService;
  }

  @PostMapping("/event")
  public EventExecutionResponse startEvent(@RequestBody EventExecutionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    CreateExecutionResponse created =
        executionApplicationService.startEventExecution(
            tenantId,
            request.pipelineId(),
            request.triggerType(),
            request.triggerId(),
            request.parameters(),
            request.idempotencyKey());
    return new EventExecutionResponse(created.id());
  }

  public record EventExecutionRequest(
      @NotNull UUID pipelineId,
      @NotBlank String triggerType,
      @NotNull UUID triggerId,
      Map<String, Object> parameters,
      String idempotencyKey) {}

  public record EventExecutionResponse(UUID executionId) {}
}
