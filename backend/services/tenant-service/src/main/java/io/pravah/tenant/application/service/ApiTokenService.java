package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.common.security.PermissionMatcher;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.ApiTokenResponse;
import io.pravah.tenant.application.dto.CreateApiTokenRequest;
import io.pravah.tenant.application.dto.CreateApiTokenResponse;
import io.pravah.tenant.application.security.RolePermissions;
import io.pravah.tenant.application.service.RoleService.UserAuthorization;
import io.pravah.tenant.domain.model.ApiToken;
import io.pravah.tenant.domain.repository.ApiTokenRepository;
import io.pravah.tenant.infrastructure.security.ApiTokenGenerator;
import io.pravah.tenant.infrastructure.security.ApiTokenHasher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API token lifecycle management (US-10.08). */
@Service
@Transactional
public class ApiTokenService {

  private static final Logger log = LoggerFactory.getLogger(ApiTokenService.class);

  private final ApiTokenRepository apiTokenRepository;
  private final RoleService roleService;

  public ApiTokenService(ApiTokenRepository apiTokenRepository, RoleService roleService) {
    this.apiTokenRepository = apiTokenRepository;
    this.roleService = roleService;
  }

  public CreateApiTokenResponse createToken(CreateApiTokenRequest request) {
    UUID tenantId = requireTenantContext();
    UUID userId = requireUserContext();

    validateRequestedPermissions(tenantId, userId, request.permissions());

    String rawToken = ApiTokenGenerator.generate();
    String tokenHash = ApiTokenHasher.hash(rawToken);
    String permissionsJson = RolePermissions.toJson(request.permissions());

    ApiToken token =
        ApiToken.builder()
            .tenantId(tenantId)
            .userId(userId)
            .name(request.name().trim())
            .tokenHash(tokenHash)
            .permissions(permissionsJson)
            .expiresAt(request.expiresAt())
            .build();

    token = apiTokenRepository.save(token);
    log.info(
        "Created API token",
        kv("token_id", token.getId()),
        kv("user_id", userId),
        kv("tenant_id", tenantId),
        kv("expires_at", request.expiresAt()));

    return new CreateApiTokenResponse(ApiTokenResponse.from(token), rawToken);
  }

  @Transactional(readOnly = true)
  public List<ApiTokenResponse> listTokens() {
    UUID tenantId = requireTenantContext();
    return apiTokenRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(ApiTokenResponse::from)
        .toList();
  }

  public ApiTokenResponse revokeToken(UUID tokenId) {
    UUID tenantId = requireTenantContext();
    ApiToken token =
        apiTokenRepository
            .findById(tokenId)
            .orElseThrow(() -> new EntityNotFoundException("ApiToken", tokenId));

    if (!tenantId.equals(token.getTenantId())) {
      throw new EntityNotFoundException("ApiToken", tokenId);
    }
    if (token.isRevoked()) {
      throw ValidationException.of("tokenId", "Token is already revoked");
    }

    token.revoke();
    token = apiTokenRepository.save(token);
    log.info("Revoked API token", kv("token_id", tokenId), kv("tenant_id", tenantId));
    return ApiTokenResponse.from(token);
  }

  private static UUID requireTenantContext() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }

  private static UUID requireUserContext() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No user context set");
    }
    return userId;
  }

  /**
   * Validates that user can grant the requested permissions.
   *
   * <p>Users can only create tokens with permissions they themselves have (no privilege
   * escalation).
   */
  private void validateRequestedPermissions(
      UUID tenantId, UUID userId, List<String> requestedPermissions) {
    UserAuthorization auth = roleService.resolveAuthorization(tenantId, userId);
    List<String> userPermissions = auth.permissions();

    List<String> unauthorized = new ArrayList<>();
    for (String requested : requestedPermissions) {
      if (!PermissionMatcher.isGranted(requested, userPermissions)) {
        unauthorized.add(requested);
      }
    }

    if (!unauthorized.isEmpty()) {
      throw ValidationException.of(
          "permissions",
          "Cannot grant permissions you do not have: " + String.join(", ", unauthorized));
    }
  }
}
