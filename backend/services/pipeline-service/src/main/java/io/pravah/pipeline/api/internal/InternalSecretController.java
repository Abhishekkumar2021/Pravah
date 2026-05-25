package io.pravah.pipeline.api.internal;

import io.pravah.pipeline.application.ResolvedSecretValue;
import io.pravah.pipeline.application.SecretApplicationService;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal API for resolving tenant secrets during stage execution (US-02.17). */
@RestController
@RequestMapping("/api/v1/internal/secrets")
public class InternalSecretController {

  private final SecretApplicationService secretApplicationService;

  public InternalSecretController(SecretApplicationService secretApplicationService) {
    this.secretApplicationService = secretApplicationService;
  }

  @GetMapping("/{name}")
  public ResolvedSecretValue resolve(
      @PathVariable String name,
      @RequestParam UUID executionId,
      @RequestParam(required = false) Instant executionTime) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    Instant resolvedTime = executionTime != null ? executionTime : Instant.now();
    return secretApplicationService.resolveSecretValueForExecution(
        tenantId, executionId, resolvedTime, name);
  }
}
