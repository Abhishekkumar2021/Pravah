package io.pravah.gateway.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal representing an authenticated user or API token in the gateway.
 *
 * <p>Extracted from JWT claims or API token validation. Used for:
 *
 * <ul>
 *   <li>Rate limiting key extraction (per-tenant or per-token)
 *   <li>Request logging and tracing
 *   <li>Downstream header propagation
 * </ul>
 */
public record GatewayPrincipal(UUID tenantId, UUID userId, String apiTokenId) implements Principal {

  public static GatewayPrincipal fromJwt(UUID tenantId, UUID userId) {
    return new GatewayPrincipal(tenantId, userId, null);
  }

  public static GatewayPrincipal fromApiToken(UUID tenantId, String tokenId) {
    return new GatewayPrincipal(tenantId, null, tokenId);
  }

  public static GatewayPrincipal anonymous() {
    return new GatewayPrincipal(null, null, null);
  }

  @Override
  public String getName() {
    if (userId != null) {
      return userId.toString();
    }
    if (apiTokenId != null) {
      return "apitoken:" + apiTokenId;
    }
    return "anonymous";
  }

  public boolean isAuthenticated() {
    return tenantId != null;
  }
}
