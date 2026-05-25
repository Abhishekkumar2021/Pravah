package io.pravah.execution.api.internal;

import io.pravah.execution.application.JobEnvironmentSecretService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resolves job environment secrets for remote runners (internal S2S only). */
@RestController
@RequestMapping("/api/v1/internal/executions")
public class InternalJobEnvironmentController {

  private final JobEnvironmentSecretService jobEnvironmentSecretService;

  public InternalJobEnvironmentController(JobEnvironmentSecretService jobEnvironmentSecretService) {
    this.jobEnvironmentSecretService = jobEnvironmentSecretService;
  }

  @PostMapping("/{executionId}/jobs/{jobId}/environment-secrets")
  public ResolveEnvironmentSecretsResponse resolve(
      @PathVariable UUID executionId,
      @PathVariable UUID jobId,
      @jakarta.validation.Valid @RequestBody ResolveEnvironmentSecretsRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context not set");
    }
    Map<String, String> values =
        jobEnvironmentSecretService.resolve(
            tenantId, executionId, jobId, request.secretEnvironment());
    return new ResolveEnvironmentSecretsResponse(values);
  }

  public record ResolveEnvironmentSecretsRequest(
      @NotNull @NotEmpty Map<String, String> secretEnvironment) {}

  public record ResolveEnvironmentSecretsResponse(Map<String, String> values) {}
}
