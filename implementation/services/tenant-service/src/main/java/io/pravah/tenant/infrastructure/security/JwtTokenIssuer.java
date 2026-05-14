package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.pravah.common.security.JwtClaimNames;
import jakarta.annotation.PostConstruct;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues JWT access tokens using RS256 asymmetric signing.
 *
 * <p>Per ADR-009, this issuer:
 *
 * <ul>
 *   <li>Holds the RSA private key for signing (never leaves this service)
 *   <li>Issues short-lived (15 minute) access tokens
 *   <li>Embeds required claims: sub (user_id), tenant_id, jti, iat, exp
 *   <li>Exposes public keys via JWKS endpoint for verification by other services
 * </ul>
 *
 * <p>Key rotation is supported by maintaining a list of active keys. New keys are added before old
 * keys are retired, ensuring tokens signed with old keys remain valid until expiry.
 *
 * @see <a href="../../../../../../../docs/adr/ADR-009-jwt-oauth2-authentication.md">ADR-009</a>
 */
@Component
public class JwtTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenIssuer.class);

  private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final int RSA_KEY_SIZE = 2048;

  private final String issuer;
  private final CopyOnWriteArrayList<RSAKey> activeKeys = new CopyOnWriteArrayList<>();
  private volatile RSAKey currentSigningKey;
  private volatile JWSSigner currentSigner;

  public JwtTokenIssuer(@Value("${pravah.security.jwt.issuer}") String issuer) {
    this.issuer = issuer;
  }

  @PostConstruct
  public void initializeKey() {
    try {
      RSAKey rsaKey = generateNewKey();
      currentSigningKey = rsaKey;
      currentSigner = new RSASSASigner(rsaKey);
      activeKeys.add(rsaKey);
      log.info("Initialized JWT signing key", kv("kid", rsaKey.getKeyID()));
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to initialize JWT signing key", e);
    }
  }

  /**
   * Generates an access token for the given user and tenant.
   *
   * @param userId the user's UUID (becomes 'sub' claim)
   * @param tenantId the tenant's UUID (becomes 'tenant_id' claim)
   * @return the signed JWT string
   */
  public String generateAccessToken(UUID userId, UUID tenantId) {
    Instant now = Instant.now();
    Instant expiry = now.plus(ACCESS_TOKEN_LIFETIME);
    String jti = UUID.randomUUID().toString();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim(JwtClaimNames.TENANT_ID, tenantId.toString())
            .jwtID(jti)
            .issuer(issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiry))
            .build();

    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(currentSigningKey.getKeyID()).build();

    SignedJWT signedJwt = new SignedJWT(header, claims);

    try {
      signedJwt.sign(currentSigner);
    } catch (JOSEException e) {
      throw new TokenGenerationException("Failed to sign JWT", e);
    }

    log.debug(
        "Generated access token",
        kv("user_id", userId),
        kv("tenant_id", tenantId),
        kv("jti", jti),
        kv("expires_at", expiry));

    return signedJwt.serialize();
  }

  /**
   * Returns the JWKS containing all active public keys.
   *
   * <p>This should be exposed at {@code /.well-known/jwks.json} for other services to fetch.
   *
   * @return the JWK Set as JSON
   */
  public String getJwksJson() {
    List<JWK> publicKeys = activeKeys.stream().map(key -> (JWK) key.toPublicJWK()).toList();
    return new JWKSet(publicKeys).toString();
  }

  /**
   * Returns the JWKS as an object (for programmatic access).
   *
   * @return the JWK Set
   */
  public JWKSet getJwks() {
    List<JWK> publicKeys = activeKeys.stream().map(key -> (JWK) key.toPublicJWK()).toList();
    return new JWKSet(publicKeys);
  }

  /**
   * Rotates the signing key.
   *
   * <p>A new key is generated and becomes the current signing key. The old key remains in the JWKS
   * until manually removed (allowing tokens signed with the old key to remain valid until they
   * expire naturally).
   */
  public void rotateKey() {
    try {
      RSAKey newKey = generateNewKey();
      RSAKey oldKey = currentSigningKey;

      currentSigningKey = newKey;
      currentSigner = new RSASSASigner(newKey);
      activeKeys.add(newKey);

      log.info(
          "Rotated JWT signing key",
          kv("new_kid", newKey.getKeyID()),
          kv("old_kid", oldKey.getKeyID()));
    } catch (JOSEException e) {
      log.error("Failed to rotate JWT signing key", kv("error", e.getMessage()));
      throw new TokenGenerationException("Failed to rotate signing key", e);
    }
  }

  /**
   * Removes an old key from the JWKS.
   *
   * <p>Should only be called after the token lifetime (15 min) has passed since rotation.
   *
   * @param kid the key ID to remove
   */
  public void removeKey(String kid) {
    boolean removed = activeKeys.removeIf(key -> key.getKeyID().equals(kid));
    if (removed) {
      log.info("Removed old JWT signing key from JWKS", kv("kid", kid));
    } else {
      log.warn("Attempted to remove non-existent key", kv("kid", kid));
    }
  }

  private RSAKey generateNewKey() {
    String timestamp = java.time.LocalDate.now().toString();
    String kid = "pravah-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    try {
      return new RSAKeyGenerator(RSA_KEY_SIZE).keyID(kid).generate();
    } catch (JOSEException e) {
      throw new TokenGenerationException("Failed to generate RSA key", e);
    }
  }

  /** Exception thrown when token generation fails. */
  public static class TokenGenerationException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public TokenGenerationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
