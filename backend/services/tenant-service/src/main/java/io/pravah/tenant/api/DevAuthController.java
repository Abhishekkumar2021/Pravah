package io.pravah.tenant.api;

import io.pravah.tenant.application.service.RoleService;
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Local-only helper to mint a bearer JWT for UI development (US-10.01 not implemented yet).
 *
 * <p>Enabled only with {@code spring.profiles.active=local}. Call via gateway: {@code POST
 * /api/v1/auth/dev-token}.
 *
 * <p>When {@code pravah.dev.token-secret} is set, callers must send matching {@code
 * X-Pravah-Dev-Secret}.
 */
@RestController
@Profile("local")
@RequestMapping("/api/v1/auth")
public class DevAuthController {

  private static final String DEV_SECRET_HEADER = "X-Pravah-Dev-Secret";

  private final JwtTokenIssuer jwtTokenIssuer;
  private final RoleService roleService;
  private final UUID defaultTenantId;
  private final UUID defaultUserId;
  private final String tokenSecret;

  public DevAuthController(
      JwtTokenIssuer jwtTokenIssuer,
      RoleService roleService,
      @Value("${pravah.dev.tenant-id}") UUID defaultTenantId,
      @Value("${pravah.dev.user-id}") UUID defaultUserId,
      @Value("${pravah.dev.token-secret:}") String tokenSecret) {
    this.jwtTokenIssuer = jwtTokenIssuer;
    this.roleService = roleService;
    this.defaultTenantId = defaultTenantId;
    this.defaultUserId = defaultUserId;
    this.tokenSecret = tokenSecret;
  }

  @PostMapping("/dev-token")
  public DevTokenResponse mintDevToken(
      @RequestHeader(value = DEV_SECRET_HEADER, required = false) String devSecret,
      @RequestBody(required = false) DevTokenRequest request) {
    if (StringUtils.hasText(tokenSecret) && !tokenSecret.equals(devSecret)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid dev secret");
    }
    UUID tenantId =
        Optional.ofNullable(request).map(DevTokenRequest::tenantId).orElse(defaultTenantId);
    UUID userId = Optional.ofNullable(request).map(DevTokenRequest::userId).orElse(defaultUserId);
    if (tenantId == null || userId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "tenantId and userId are required when defaults are unset");
    }
    RoleService.UserAuthorization authorization =
        roleService.resolveAuthorization(tenantId, userId);
    String accessToken =
        jwtTokenIssuer.generateAccessToken(
            userId, tenantId, List.of(authorization.role().name()), authorization.permissions());
    Instant expiresAt = Instant.now().plusSeconds(15 * 60);
    return new DevTokenResponse(accessToken, userId, tenantId, expiresAt);
  }

  public record DevTokenRequest(UUID userId, UUID tenantId) {}

  public record DevTokenResponse(
      String accessToken, UUID userId, UUID tenantId, Instant expiresAt) {}
}
