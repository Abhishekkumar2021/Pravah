package io.pravah.pipeline.api;

import io.pravah.pipeline.api.dto.CreateSecretRequest;
import io.pravah.pipeline.api.dto.SecretResponse;
import io.pravah.pipeline.api.dto.UpdateSecretRequest;
import io.pravah.pipeline.application.SecretApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for tenant secret references.
 *
 * <p>Secrets API manages references (name → provider + path) but never returns actual secret
 * values. Values are resolved at pipeline execution time.
 */
@RestController
@RequestMapping("/api/v1/secrets")
@Validated
public class SecretController {

  private final SecretApplicationService secretService;

  public SecretController(SecretApplicationService secretService) {
    this.secretService = secretService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public SecretResponse createSecret(@Valid @RequestBody CreateSecretRequest request) {
    return secretService.createSecret(request);
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public List<SecretResponse> listSecrets() {
    return secretService.listSecrets();
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public SecretResponse getSecret(@PathVariable UUID id) {
    return secretService.getSecret(id);
  }

  @GetMapping("/by-name/{name}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public SecretResponse getSecretByName(
      @PathVariable
          @Pattern(
              regexp = "^[a-z_][a-z0-9_]*$",
              message = "name must be lowercase alphanumeric with underscores")
          String name) {
    return secretService.getSecretByName(name);
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public SecretResponse updateSecret(
      @PathVariable UUID id, @Valid @RequestBody UpdateSecretRequest request) {
    return secretService.updateSecret(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public void deleteSecret(@PathVariable UUID id) {
    secretService.deleteSecret(id);
  }
}
