package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.pravah.common.security.JwtClaimNames;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JwtTokenVerifierValidationTest {

  @Test
  void validateAndGetClaims_usesCachedJwks() throws Exception {
    var rsaKey = new RSAKeyGenerator(2048).keyID("test").generate();
    var verifier = new JwtTokenVerifier("http://localhost/unused", "");
    setCachedJwks(verifier, new JWKSet(rsaKey));

    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = signToken(rsaKey, userId, tenantId);

    var claims = verifier.validateAndGetClaims(token);

    assertThat(claims.userId()).isEqualTo(userId);
    assertThat(claims.tenantId()).isEqualTo(tenantId);
    assertThat(verifier.isValidToken(token)).isTrue();
    assertThat(verifier.getUserId(token)).isEqualTo(userId);
    assertThat(verifier.getTenantId(token)).isEqualTo(tenantId);
  }

  @Test
  void validateAndGetClaims_withoutJwks_throws() {
    var verifier = new JwtTokenVerifier("http://localhost/unused", "");

    assertThatThrownBy(() -> verifier.validateAndGetClaims("any.jwt.token"))
        .isInstanceOf(JwtVerificationException.class)
        .hasMessageContaining("No JWKS");
  }

  private static void setCachedJwks(JwtTokenVerifier verifier, JWKSet jwkSet) throws Exception {
    Field field = JwtTokenVerifier.class.getDeclaredField("cachedJwkSet");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<JWKSet> ref = (AtomicReference<JWKSet>) field.get(verifier);
    ref.set(jwkSet);
  }

  private static String signToken(com.nimbusds.jose.jwk.RSAKey rsaKey, UUID userId, UUID tenantId)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim(JwtClaimNames.TENANT_ID, tenantId.toString())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(900)))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(rsaKey.toRSAPrivateKey()));
    return jwt.serialize();
  }
}
