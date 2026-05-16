package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.pravah.common.security.JwtClaimNames;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenIssuerTest {

  private static final String TEST_ISSUER = "pravah-test";

  private JwtTokenIssuer jwtTokenIssuer;

  @BeforeEach
  void setUp() {
    jwtTokenIssuer = new JwtTokenIssuer(TEST_ISSUER);
    jwtTokenIssuer.initializeKey();
  }

  @Test
  void generateAccessToken_containsRequiredClaims() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    String token = jwtTokenIssuer.generateAccessToken(userId, tenantId);

    JWTClaimsSet claims = validateAndGetClaims(token);

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getStringClaim("tenant_id")).isEqualTo(tenantId.toString());
    assertThat(claims.getIssuer()).isEqualTo(TEST_ISSUER);
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getIssueTime()).isNotNull();
    assertThat(claims.getExpirationTime()).isNotNull();
  }

  @Test
  void generateAccessToken_includesRolesAndPermissions() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    String token =
        jwtTokenIssuer.generateAccessToken(
            userId, tenantId, List.of("editor"), List.of("pipelines:write"));

    JWTClaimsSet claims = validateAndGetClaims(token);
    assertThat(claims.getStringListClaim(JwtClaimNames.ROLES)).containsExactly("editor");
    assertThat(claims.getStringListClaim(JwtClaimNames.PERMISSIONS))
        .containsExactly("pipelines:write");
  }

  @Test
  void generateAccessToken_hasCorrectExpiry() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    Instant beforeGeneration = Instant.now();
    String token = jwtTokenIssuer.generateAccessToken(userId, tenantId);
    Instant afterGeneration = Instant.now();

    JWTClaimsSet claims = validateAndGetClaims(token);

    Instant expiry = claims.getExpirationTime().toInstant();
    Instant expectedMinExpiry = beforeGeneration.plusSeconds(14 * 60);
    Instant expectedMaxExpiry = afterGeneration.plusSeconds(16 * 60);

    assertThat(expiry).isAfter(expectedMinExpiry).isBefore(expectedMaxExpiry);
  }

  @Test
  void generateAccessToken_usesRS256Algorithm() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    String token = jwtTokenIssuer.generateAccessToken(userId, tenantId);

    String[] parts = token.split("\\.");
    assertThat(parts).hasSize(3);

    String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
    assertThat(headerJson).contains("\"alg\":\"RS256\"");
    assertThat(headerJson).contains("\"kid\":");
  }

  @Test
  void getJwksJson_returnsValidJwks() {
    String jwksJson = jwtTokenIssuer.getJwksJson();

    assertThat(jwksJson).contains("\"keys\"");
    assertThat(jwksJson).contains("\"kty\":\"RSA\"");
    assertThat(jwksJson).contains("\"kid\":");
    assertThat(jwksJson).contains("\"n\":");
    assertThat(jwksJson).contains("\"e\":");
    assertThat(jwksJson).doesNotContain("\"d\":"); // Private key component must not be exposed
  }

  @Test
  void getJwks_returnsJwkSet() {
    JWKSet jwks = jwtTokenIssuer.getJwks();

    assertThat(jwks.getKeys()).hasSize(1);
    assertThat(jwks.getKeys().get(0).isPrivate()).isFalse();
    String keyId = jwks.getKeys().get(0).getKeyID();
    assertThat(keyId).startsWith("pravah-");
    assertThat(keyId).containsPattern("pravah-\\d{4}-\\d{2}-\\d{2}-[a-f0-9]{8}");
  }

  @Test
  void rotateKey_addsNewKeyToJwks() {
    JWKSet initialJwks = jwtTokenIssuer.getJwks();
    String initialKid = initialJwks.getKeys().get(0).getKeyID();

    jwtTokenIssuer.rotateKey();

    JWKSet afterRotation = jwtTokenIssuer.getJwks();
    assertThat(afterRotation.getKeys()).hasSize(2);

    assertThat(afterRotation.getKeyByKeyId(initialKid)).isNotNull();
  }

  @Test
  void rotateKey_newTokensUsedNewKey() throws Exception {
    JWKSet initialJwks = jwtTokenIssuer.getJwks();
    String initialKid = initialJwks.getKeys().get(0).getKeyID();

    jwtTokenIssuer.rotateKey();

    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenIssuer.generateAccessToken(userId, tenantId);

    String[] parts = token.split("\\.");
    String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
    assertThat(headerJson).doesNotContain(initialKid);
  }

  @Test
  void rotateKey_oldTokensStillValid() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String tokenBeforeRotation = jwtTokenIssuer.generateAccessToken(userId, tenantId);

    jwtTokenIssuer.rotateKey();

    JWTClaimsSet claims = validateAndGetClaims(tokenBeforeRotation);
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
  }

  @Test
  void removeKey_removesKeyFromJwks() {
    jwtTokenIssuer.rotateKey();
    JWKSet afterRotation = jwtTokenIssuer.getJwks();
    assertThat(afterRotation.getKeys()).hasSize(2);

    String oldKid = afterRotation.getKeys().get(0).getKeyID();
    jwtTokenIssuer.removeKey(oldKid);

    JWKSet afterRemoval = jwtTokenIssuer.getJwks();
    assertThat(afterRemoval.getKeys()).hasSize(1);
    assertThat(afterRemoval.getKeyByKeyId(oldKid)).isNull();
  }

  @Test
  void multipleTokens_haveDifferentJwtIds() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    String token1 = jwtTokenIssuer.generateAccessToken(userId, tenantId);
    String token2 = jwtTokenIssuer.generateAccessToken(userId, tenantId);

    assertThat(token1).isNotEqualTo(token2);
  }

  private JWTClaimsSet validateAndGetClaims(String token) throws Exception {
    var processor = new DefaultJWTProcessor<>();
    var keySelector =
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwtTokenIssuer.getJwks()));
    processor.setJWSKeySelector(keySelector);
    return processor.process(token, null);
  }
}
