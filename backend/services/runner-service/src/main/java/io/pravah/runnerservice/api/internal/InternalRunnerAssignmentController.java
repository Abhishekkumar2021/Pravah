package io.pravah.runnerservice.api.internal;

import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.runnerservice.domain.JobAssignment;
import io.pravah.runnerservice.service.JobAssignmentService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service runner job assignment (internal secret + tenant header). */
@RestController
@RequestMapping("/api/v1/internal/runners")
public class InternalRunnerAssignmentController {

  private final JobAssignmentService jobAssignmentService;

  public InternalRunnerAssignmentController(JobAssignmentService jobAssignmentService) {
    this.jobAssignmentService = jobAssignmentService;
  }

  @PostMapping("/assignments")
  public ResponseEntity<AssignmentDto> assignJob(@Valid @RequestBody AssignJobRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context not set");
    }
    return jobAssignmentService
        .assignJob(
            tenantId,
            request.jobId(),
            request.executionId(),
            request.pipelineId(),
            request.jobName(),
            request.spec(),
            request.labels() != null ? request.labels() : Map.of())
        .map(a -> ResponseEntity.ok(toDto(a)))
        .orElse(ResponseEntity.status(503).build());
  }

  private static AssignmentDto toDto(JobAssignment a) {
    return new AssignmentDto(
        a.getId(), a.getRunnerId(), a.getJobId(), a.getExecutionId(), a.getStatus());
  }

  public record AssignJobRequest(
      @NotNull UUID jobId,
      @NotNull UUID executionId,
      UUID pipelineId,
      String jobName,
      @NotNull @Valid RemoteJobSpecPayload spec,
      Map<String, String> labels) {}

  public record AssignmentDto(
      UUID assignmentId, UUID runnerId, UUID jobId, UUID executionId, String status) {}
}
