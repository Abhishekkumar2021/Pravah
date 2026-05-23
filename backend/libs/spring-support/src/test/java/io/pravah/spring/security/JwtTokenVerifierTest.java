package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import io.pravah.test.security.TestJwtIssuer;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenVerifierTest {

  private TestJwtIssuer testJwtIssuer;

  @BeforeEach
  void setUp() {
    testJwtIssuer = new TestJwtIssuer();
  }

  @Test
  void validateAndGetClaims_validToken_returnsClaims() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);
    JwtClaims claims = verifier.validateAndGetClaims(token);

    assertThat(claims.userId()).isEqualTo(userId);
    assertThat(claims.tenantId()).isEqualTo(tenantId);
    assertThat(claims.jwtId()).isNotBlank();
    assertThat(claims.issuedAt()).isNotNull();
    assertThat(claims.expiresAt()).isNotNull();
    assertThat(claims.expiresAt()).isAfter(Instant.now());
  }

  @Test
  void isValidToken_validToken_returnsTrue() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);

    assertThat(verifier.isValidToken(token)).isTrue();
  }

  @Test
  void isValidToken_nullToken_returnsFalse() {
    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);
    assertThat(verifier.isValidToken(null)).isFalse();
  }

  @Test
  void isValidToken_blankToken_returnsFalse() {
    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);
    assertThat(verifier.isValidToken("   ")).isFalse();
  }

  @Test
  void isValidToken_malformedToken_returnsFalse() {
    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);
    assertThat(verifier.isValidToken("not.a.valid.token")).isFalse();
  }

  @Test
  void getUserId_validToken_returnsUserId() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);

    assertThat(verifier.getUserId(token)).isEqualTo(userId);
  }

  @Test
  void getTenantId_validToken_returnsTenantId() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);

    assertThat(verifier.getTenantId(token)).isEqualTo(tenantId);
  }

  @Test
  void validateAndGetClaims_invalidToken_throwsException() {
    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);

    assertThatThrownBy(() -> verifier.validateAndGetClaims("invalid.token.here"))
        .isInstanceOf(JwtVerificationException.class);
  }

  @Test
  void validateAndGetClaims_customExpiry_containsCorrectExpiry() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Duration customExpiry = Duration.ofHours(1);

    String token = testJwtIssuer.generateAccessToken(userId, tenantId, customExpiry);

    TestJwtVerifier verifier = new TestJwtVerifier(testJwtIssuer);
    JwtClaims claims = verifier.validateAndGetClaims(token);

    assertThat(claims.expiresAt())
        .isAfter(Instant.now().plus(Duration.ofMinutes(50)))
        .isBefore(Instant.now().plus(Duration.ofMinutes(70)));
  }

  @Test
  void validateAndGetClaims_tokenFromDifferentIssuer_throwsException() {
    TestJwtIssuer issuer1 = new TestJwtIssuer("issuer-1");
    TestJwtIssuer issuer2 = new TestJwtIssuer("issuer-2");

    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = issuer1.generateAccessToken(userId, tenantId);

    TestJwtVerifier verifier = new TestJwtVerifier(issuer2);

    assertThatThrownBy(() -> verifier.validateAndGetClaims(token))
        .isInstanceOf(JwtVerificationException.class);
  }

  /**
   * Test verifier that uses the TestJwtIssuer's JWKS directly, avoiding HTTP calls. Uses the
   * protected extractClaims method from the base class.
   */
  private static class TestJwtVerifier extends JwtTokenVerifier {

    private final TestJwtIssuer issuer;

    TestJwtVerifier(TestJwtIssuer issuer) {
      super("http://localhost/.well-known/jwks.json", "");
      this.issuer = issuer;
    }

    @Override
    public JwtClaims validateAndGetClaims(String token) {
      try {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        var keySelector =
            new JWSVerificationKeySelector<SecurityContext>(
                JWSAlgorithm.RS256, new ImmutableJWKSet<>(issuer.getJwks()));
        processor.setJWSKeySelector(keySelector);

        var claimsSet = processor.process(token, null);
        return extractClaims(claimsSet);
      } catch (ParseException e) {
        throw new JwtVerificationException("Invalid JWT format", e);
      } catch (BadJOSEException e) {
        throw new JwtVerificationException("JWT verification failed: " + e.getMessage(), e);
      } catch (JOSEException e) {
        throw new JwtVerificationException("JWT processing error", e);
      }
    }

    @Override
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

    @Override
    public UUID getUserId(String token) {
      return validateAndGetClaims(token).userId();
    }

    @Override
    public UUID getTenantId(String token) {
      return validateAndGetClaims(token).tenantId();
    }
  }
}
