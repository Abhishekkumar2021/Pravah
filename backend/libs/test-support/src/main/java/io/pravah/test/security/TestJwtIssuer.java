package io.pravah.test.security;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Test utility for generating RS256-signed JWT tokens in integration tests.
 *
 * <p>This class provides the same token generation capability as the production {@code
 * JwtTokenIssuer} in tenant-service, allowing integration tests to create valid tokens without
 * requiring the full auth infrastructure.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Autowired TestJwtIssuer testJwtIssuer;
 *
 * @Test
 * void myTest() {
 *     String token = testJwtIssuer.generateAccessToken(userId, tenantId);
 *     mockMvc.perform(get("/api/...")
 *         .header("Authorization", "Bearer " + token))
 *         ...
 * }
 * }</pre>
 *
 * <p>The test JWKS is served via {@link TestJwksController} which should be available at {@code
 * /.well-known/jwks.json} in test configurations.
 */
public class TestJwtIssuer {

  private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final String DEFAULT_ISSUER = "pravah-test";
  private static final int RSA_KEY_SIZE = 2048;

  private final RSAKey signingKey;
  private final JWSSigner signer;
  private final String issuer;

  public TestJwtIssuer() {
    this(DEFAULT_ISSUER);
  }

  public TestJwtIssuer(String issuer) {
    this.issuer = issuer;
    try {
      this.signingKey = new RSAKeyGenerator(RSA_KEY_SIZE).keyID("test-key-1").generate();
      this.signer = new RSASSASigner(signingKey);
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to generate test RSA key", e);
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

    return signToken(userId, tenantId, now, expiry, jti, List.of("owner"), List.of("*"));
  }

  /**
   * Generates an access token with custom expiry for testing expiry scenarios.
   *
   * @param userId the user's UUID
   * @param tenantId the tenant's UUID
   * @param validFor the duration the token should be valid
   * @return the signed JWT string
   */
  public String generateAccessToken(UUID userId, UUID tenantId, Duration validFor) {
    Instant now = Instant.now();
    Instant expiry = now.plus(validFor);
    String jti = UUID.randomUUID().toString();

    return signToken(userId, tenantId, now, expiry, jti, List.of("owner"), List.of("*"));
  }

  /** Generates a token with explicit roles and permissions (for authorization tests). */
  public String generateAccessToken(
      UUID userId, UUID tenantId, List<String> roles, List<String> permissions) {
    Instant now = Instant.now();
    Instant expiry = now.plus(ACCESS_TOKEN_LIFETIME);
    String jti = UUID.randomUUID().toString();
    return signToken(userId, tenantId, now, expiry, jti, roles, permissions);
  }

  private String signToken(
      UUID userId,
      UUID tenantId,
      Instant now,
      Instant expiry,
      String jti,
      List<String> roles,
      List<String> permissions) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim(JwtClaimNames.TENANT_ID, tenantId.toString())
            .claim(JwtClaimNames.ROLES, roles)
            .claim(JwtClaimNames.PERMISSIONS, permissions)
            .jwtID(jti)
            .issuer(issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiry))
            .build();

    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build();

    SignedJWT signedJwt = new SignedJWT(header, claims);

    try {
      signedJwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign test JWT", e);
    }

    return signedJwt.serialize();
  }

  /**
   * Returns the JWKS JSON containing the public key for token verification.
   *
   * @return the JWKS as a JSON string
   */
  public String getJwksJson() {
    List<JWK> publicKeys = List.of(signingKey.toPublicJWK());
    return new JWKSet(publicKeys).toString();
  }

  /**
   * Returns the JWKS containing the public key.
   *
   * @return the JWK Set
   */
  public JWKSet getJwks() {
    return new JWKSet(signingKey.toPublicJWK());
  }

  /**
   * Returns the public key for direct verification (useful for unit tests).
   *
   * @return the RSA public key as JWK
   */
  public RSAKey getPublicKey() {
    return signingKey.toPublicJWK();
  }
}
