package io.pravah.tenant.api.rest;

import io.pravah.tenant.application.dto.ApiTokenResponse;
import io.pravah.tenant.application.dto.CreateApiTokenRequest;
import io.pravah.tenant.application.dto.CreateApiTokenResponse;
import io.pravah.tenant.application.service.ApiTokenService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for API token management (US-10.08). */
@RestController
@RequestMapping("/api/v1/api-tokens")
public class ApiTokenController {

  private final ApiTokenService apiTokenService;

  public ApiTokenController(ApiTokenService apiTokenService) {
    this.apiTokenService = apiTokenService;
  }

  @PostMapping
  @PreAuthorize(
      "@permissionChecker.hasAny('api_tokens:write', 'api_tokens:*', 'users:write', 'users:*')")
  public ResponseEntity<CreateApiTokenResponse> createToken(
      @Valid @RequestBody CreateApiTokenRequest request) {
    CreateApiTokenResponse response = apiTokenService.createToken(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  @PreAuthorize(
      "@permissionChecker.hasAny('api_tokens:read', 'api_tokens:*', 'users:read', 'users:*')")
  public ResponseEntity<List<ApiTokenResponse>> listTokens() {
    return ResponseEntity.ok(apiTokenService.listTokens());
  }

  @DeleteMapping("/{tokenId}")
  @PreAuthorize(
      "@permissionChecker.hasAny('api_tokens:write', 'api_tokens:*', 'users:write', 'users:*')")
  public ResponseEntity<ApiTokenResponse> revokeToken(@PathVariable UUID tokenId) {
    return ResponseEntity.ok(apiTokenService.revokeToken(tokenId));
  }
}
