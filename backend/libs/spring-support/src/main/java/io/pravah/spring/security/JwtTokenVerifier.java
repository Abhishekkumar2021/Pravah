package io.pravah.spring.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.pravah.common.security.JwtClaimNames;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Verifies JWT access tokens using RS256 public keys from a JWKS endpoint.
 *
 * <p>Per ADR-009, this verifier:
 *
 * <ul>
 *   <li>Fetches public keys from the JWKS endpoint (exposed by tenant-service)
 *   <li>Caches keys with periodic refresh (every 5 minutes)
 *   <li>Verifies RS256 signatures without access to the private key
 *   <li>Extracts standard claims: sub (user_id), tenant_id
 * </ul>
 *
 * <p>Only the issuing service (tenant-service) holds the private signing key. All other services
 * verify tokens using this class with only the public key.
 *
 * @see <a href="../../../../../../docs/adr/ADR-009-jwt-oauth2-authentication.md">ADR-009</a>
 */
/**
 * HTTP JWKS verifier for non-issuer services. Disabled on tenant-service when {@link
 * io.pravah.tenant.infrastructure.security.LocalJwtTokenVerifier} is registered ({@code
 * pravah.security.jwt.use-remote-jwks=false}).
 */
@Component
@ConditionalOnProperty(
    name = "pravah.security.jwt.use-remote-jwks",
    havingValue = "true",
    matchIfMissing = true)
public class JwtTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenVerifier.class);

  private static final Duration JWKS_REFRESH_INTERVAL = Duration.ofMinutes(5);
  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

  private final String jwksUrl;
  private final String expectedIssuer;
  private final AtomicReference<JWKSet> cachedJwkSet = new AtomicReference<>();
  private volatile Instant lastRefresh = Instant.EPOCH;

  public JwtTokenVerifier(
      @Value("${pravah.security.jwt.jwks-url}") String jwksUrl,
      @Value("${pravah.security.jwt.issuer:}") String expectedIssuer) {
    this.jwksUrl = jwksUrl;
    this.expectedIssuer = expectedIssuer;
  }

  /**
   * Validates a JWT and returns the claims if valid.
   *
   * @param token the JWT string
   * @return the validated claims
   * @throws JwtVerificationException if the token is invalid, expired, or cannot be verified
   */
  public JwtClaims validateAndGetClaims(String token) {
    refreshJwksIfNeeded();

    JWKSet jwkSet = cachedJwkSet.get();
    if (jwkSet == null || jwkSet.getKeys().isEmpty()) {
      throw new JwtVerificationException("No JWKS available for token verification");
    }

    try {
      ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwkSet));
      processor.setJWSKeySelector(keySelector);

      JWTClaimsSet claimsSet = processor.process(token, null);
      return extractClaims(claimsSet);

    } catch (ParseException e) {
      log.debug("JWT parse error", kv("error", e.getMessage()));
      throw new JwtVerificationException("Invalid JWT format", e);
    } catch (BadJOSEException e) {
      log.debug("JWT verification failed", kv("error", e.getMessage()));
      throw new JwtVerificationException("JWT verification failed: " + e.getMessage(), e);
    } catch (JOSEException e) {
      log.debug("JWT processing error", kv("error", e.getMessage()));
      throw new JwtVerificationException("JWT processing error", e);
    }
  }

  /**
   * Checks if a token is valid without throwing exceptions.
   *
   * @param token the JWT string
   * @return true if valid, false otherwise
   */
  public boolean isValidToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      validateAndGetClaims(token);
      return true;
    } catch (JwtVerificationException e) {
      return false;
    }
  }

  /**
   * Extracts user ID from a validated token.
   *
   * @param token the JWT string
   * @return the user UUID
   * @throws JwtVerificationException if token is invalid
   */
  public UUID getUserId(String token) {
    return validateAndGetClaims(token).userId();
  }

  /**
   * Extracts tenant ID from a validated token.
   *
   * @param token the JWT string
   * @return the tenant UUID
   * @throws JwtVerificationException if token is invalid
   */
  public UUID getTenantId(String token) {
    return validateAndGetClaims(token).tenantId();
  }

  /** Scheduled task to refresh JWKS cache. */
  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
  public void scheduledJwksRefresh() {
    refreshJwksWithRetry();
  }

  private void refreshJwksIfNeeded() {
    Instant now = Instant.now();
    if (cachedJwkSet.get() == null
        || Duration.between(lastRefresh, now).compareTo(JWKS_REFRESH_INTERVAL) > 0) {
      refreshJwksWithRetry();
    }
  }

  private synchronized void refreshJwksWithRetry() {
    for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        log.debug("Refreshing JWKS from {}", kv("url", jwksUrl), kv("attempt", attempt));
        JWKSet jwkSet = JWKSet.load(URI.create(jwksUrl).toURL());
        cachedJwkSet.set(jwkSet);
        lastRefresh = Instant.now();
        log.info("JWKS refreshed", kv("key_count", jwkSet.getKeys().size()));
        return;
      } catch (IOException | ParseException e) {
        log.warn(
            "Failed to refresh JWKS",
            kv("url", jwksUrl),
            kv("attempt", attempt),
            kv("max_attempts", MAX_RETRY_ATTEMPTS),
            kv("error", e.getMessage()));

        if (attempt < MAX_RETRY_ATTEMPTS) {
          try {
            Thread.sleep(RETRY_DELAY.toMillis() * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("JWKS refresh retry interrupted");
            return;
          }
        }
      }
    }
    log.error(
        "Failed to refresh JWKS after all retries",
        kv("url", jwksUrl),
        kv("max_attempts", MAX_RETRY_ATTEMPTS));
  }

  /**
   * Extracts JWT claims from a validated claims set.
   *
   * <p>This method is protected to allow subclasses (e.g., LocalJwtTokenVerifier) to reuse the
   * claims extraction logic.
   *
   * @param claimsSet the validated JWT claims set
   * @return the extracted claims
   * @throws JwtVerificationException if required claims are missing or invalid
   */
  protected JwtClaims extractClaims(JWTClaimsSet claimsSet) {
    if (expectedIssuer != null && !expectedIssuer.isBlank()) {
      String issuer = claimsSet.getIssuer();
      if (issuer == null || !expectedIssuer.equals(issuer)) {
        throw new JwtVerificationException("JWT issuer mismatch");
      }
    }

    String subject = claimsSet.getSubject();
    if (subject == null) {
      throw new JwtVerificationException("JWT missing subject claim");
    }

    Object tenantIdClaim = claimsSet.getClaim(JwtClaimNames.TENANT_ID);
    if (tenantIdClaim == null) {
      throw new JwtVerificationException("JWT missing tenant_id claim");
    }

    try {
      UUID userId = UUID.fromString(subject);
      UUID tenantId = UUID.fromString(tenantIdClaim.toString());

      return new JwtClaims(
          userId,
          tenantId,
          claimsSet.getJWTID(),
          claimsSet.getIssueTime() != null ? claimsSet.getIssueTime().toInstant() : null,
          claimsSet.getExpirationTime() != null ? claimsSet.getExpirationTime().toInstant() : null,
          parseStringListClaim(claimsSet, JwtClaimNames.ROLES),
          parseStringListClaim(claimsSet, JwtClaimNames.PERMISSIONS));
    } catch (IllegalArgumentException e) {
      throw new JwtVerificationException("Invalid UUID in JWT claims", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> parseStringListClaim(JWTClaimsSet claimsSet, String claimName) {
    Object raw = claimsSet.getClaim(claimName);
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      return list.stream().map(Object::toString).toList();
    }
    return List.of(raw.toString());
  }

  /** Validated JWT claims. */
  public record JwtClaims(
      UUID userId,
      UUID tenantId,
      String jwtId,
      Instant issuedAt,
      Instant expiresAt,
      List<String> roles,
      List<String> permissions) {

    public JwtClaims {
      roles = roles != null ? List.copyOf(roles) : List.of();
      permissions = permissions != null ? List.copyOf(permissions) : List.of();
    }

    public JwtClaims(
        UUID userId, UUID tenantId, String jwtId, Instant issuedAt, Instant expiresAt) {
      this(
          userId,
          tenantId,
          jwtId,
          issuedAt,
          expiresAt,
          Collections.emptyList(),
          Collections.emptyList());
    }
  }

  /** Exception thrown when JWT verification fails. */
  public static class JwtVerificationException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public JwtVerificationException(String message) {
      super(message);
    }

    public JwtVerificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
