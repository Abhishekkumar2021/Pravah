package io.pravah.runnerservice.api;

import io.pravah.runnerservice.infrastructure.client.ExecutionJobEnvironmentClient;
import io.pravah.runnerservice.infrastructure.security.RunnerAgentRequestAttributes;
import io.pravah.runnerservice.service.JobAssignmentService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runner-agent API for resolving job environment secrets at execution time.
 *
 * <p>Authenticated via {@link
 * io.pravah.runnerservice.infrastructure.security.RunnerAgentAuthFilter} using the runner stream
 * token — not the internal service secret.
 */
@RestController
@RequestMapping("/api/v1/runners/{runnerId}/jobs")
public class RunnerAgentJobEnvironmentController {

  private static final Logger log =
      LoggerFactory.getLogger(RunnerAgentJobEnvironmentController.class);

  private final ExecutionJobEnvironmentClient executionJobEnvironmentClient;
  private final JobAssignmentService jobAssignmentService;

  public RunnerAgentJobEnvironmentController(
      ExecutionJobEnvironmentClient executionJobEnvironmentClient,
      JobAssignmentService jobAssignmentService) {
    this.executionJobEnvironmentClient = executionJobEnvironmentClient;
    this.jobAssignmentService = jobAssignmentService;
  }

  @PostMapping("/{jobId}/environment-secrets")
  @ResponseStatus(HttpStatus.OK)
  public ResolveEnvironmentSecretsResponse resolve(
      @PathVariable UUID runnerId,
      @PathVariable UUID jobId,
      @Valid @RequestBody ResolveEnvironmentSecretsRequest request,
      HttpServletRequest httpRequest) {
    UUID authenticatedRunnerId =
        (UUID) httpRequest.getAttribute(RunnerAgentRequestAttributes.RUNNER_ID);
    if (authenticatedRunnerId == null || !authenticatedRunnerId.equals(runnerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Runner identity mismatch");
    }
    if (!jobAssignmentService.isAssignedToRunner(jobId, runnerId)) {
      log.warn(
          "Secret resolution denied: job not assigned to runner",
          net.logstash.logback.argument.StructuredArguments.kv("job_id", jobId),
          net.logstash.logback.argument.StructuredArguments.kv("runner_id", runnerId));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Job not assigned to this runner");
    }
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Resolving job environment secrets for runner",
        net.logstash.logback.argument.StructuredArguments.kv("job_id", jobId),
        net.logstash.logback.argument.StructuredArguments.kv("runner_id", runnerId),
        net.logstash.logback.argument.StructuredArguments.kv("tenant_id", tenantId),
        net.logstash.logback.argument.StructuredArguments.kv(
            "secret_keys", request.secretEnvironment().keySet()));
    Map<String, String> values =
        executionJobEnvironmentClient.resolve(
            tenantId, request.executionId(), jobId, request.secretEnvironment());
    return new ResolveEnvironmentSecretsResponse(values);
  }

  public record ResolveEnvironmentSecretsRequest(
      @NotNull UUID executionId, @NotNull @NotEmpty Map<String, String> secretEnvironment) {}

  public record ResolveEnvironmentSecretsResponse(Map<String, String> values) {}
}
