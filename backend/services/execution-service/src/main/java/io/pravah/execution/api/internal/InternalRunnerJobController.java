package io.pravah.execution.api.internal;

import io.pravah.execution.application.RemoteJobCompletionService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/jobs")
public class InternalRunnerJobController {

  private final RemoteJobCompletionService remoteJobCompletionService;

  public InternalRunnerJobController(RemoteJobCompletionService remoteJobCompletionService) {
    this.remoteJobCompletionService = remoteJobCompletionService;
  }

  @PostMapping("/{jobId}/complete")
  public void completeJob(
      @PathVariable UUID jobId, @RequestBody RemoteJobCompletionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context not set");
    }
    remoteJobCompletionService.completeJob(
        tenantId,
        jobId,
        request.runnerId(),
        request.exitCode(),
        request.output() != null ? request.output() : Map.of());
  }

  public record RemoteJobCompletionRequest(
      @NotNull Integer exitCode, UUID runnerId, Map<String, Object> output) {}
}
