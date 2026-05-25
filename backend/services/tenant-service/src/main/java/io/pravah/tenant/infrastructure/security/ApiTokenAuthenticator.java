package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.tenant.application.security.RolePermissions;
import io.pravah.tenant.domain.model.ApiToken;
import io.pravah.tenant.domain.repository.ApiTokenRepository;
import io.pravah.tenant.infrastructure.persistence.AuthRlsHelper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Validates {@code prv_1_} API tokens and records usage (US-10.08). */
@Component
public class ApiTokenAuthenticator {

  private static final Logger log = LoggerFactory.getLogger(ApiTokenAuthenticator.class);

  private final ApiTokenRepository apiTokenRepository;
  private final AuthRlsHelper authRlsHelper;

  public ApiTokenAuthenticator(ApiTokenRepository apiTokenRepository, AuthRlsHelper authRlsHelper) {
    this.apiTokenRepository = apiTokenRepository;
    this.authRlsHelper = authRlsHelper;
  }

  /**
   * Authenticates a raw API token string.
   *
   * @param rawToken the bearer value (including {@code prv_1_} prefix)
   * @return resolved identity if valid and active
   */
  @Transactional
  public Optional<AuthenticatedApiToken> authenticate(String rawToken) {
    if (!ApiTokenGenerator.isApiToken(rawToken)) {
      return Optional.empty();
    }

    String tokenHash = ApiTokenHasher.hash(rawToken);
    Optional<ApiToken> tokenOpt;
    try {
      authRlsHelper.enableApiTokenLookup();
      tokenOpt = apiTokenRepository.findByTokenHash(tokenHash);
    } finally {
      authRlsHelper.disableApiTokenLookup();
    }

    if (tokenOpt.isEmpty()) {
      log.info("API token authentication failed: token not found");
      return Optional.empty();
    }

    ApiToken token = tokenOpt.get();
    Instant now = Instant.now();
    if (!token.isActive(now)) {
      log.info(
          "API token authentication failed: token inactive",
          kv("token_id", token.getId()),
          kv("revoked", token.isRevoked()),
          kv("expired", token.isExpired(now)));
      return Optional.empty();
    }

    token.recordUsage(now);
    apiTokenRepository.save(token);

    List<String> permissions = RolePermissions.parse(token.getPermissions());
    return Optional.of(
        new AuthenticatedApiToken(
            token.getId(), token.getTenantId(), token.getUserId(), permissions));
  }

  /** Authenticated API token identity for filters and authorization. */
  public record AuthenticatedApiToken(
      UUID tokenId, UUID tenantId, UUID userId, List<String> permissions) {}
}
