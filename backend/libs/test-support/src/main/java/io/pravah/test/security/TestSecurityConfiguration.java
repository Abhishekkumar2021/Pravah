package io.pravah.test.security;

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
import io.pravah.spring.security.JwtTokenVerifier;
import java.text.ParseException;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides JWT infrastructure for integration tests.
 *
 * <p>This configuration provides:
 *
 * <ul>
 *   <li>{@link TestJwtIssuer} - for generating valid RS256 tokens in tests
 *   <li>A test-friendly {@link JwtTokenVerifier} that verifies tokens using the test issuer's
 *       public key (bypassing HTTP JWKS fetch)
 * </ul>
 *
 * <p>Usage in tests:
 *
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestSecurityConfiguration.class)
 * class MyIntegrationTest {
 *     @Autowired TestJwtIssuer testJwtIssuer;
 *
 *     @Test
 *     void test() {
 *         String token = testJwtIssuer.generateAccessToken(userId, tenantId);
 *         // Use token in requests...
 *     }
 * }
 * }</pre>
 */
@TestConfiguration
public class TestSecurityConfiguration {

  @Bean
  public TestJwtIssuer testJwtIssuer() {
    return new TestJwtIssuer();
  }

  /**
   * Provides a test-friendly JWT verifier that uses the TestJwtIssuer's public key directly,
   * avoiding the need to run an HTTP JWKS endpoint.
   */
  @Bean
  @Primary
  public JwtTokenVerifier jwtTokenVerifier(TestJwtIssuer testJwtIssuer) {
    return new TestJwtTokenVerifier(testJwtIssuer.getJwks());
  }

  /**
   * A JwtTokenVerifier implementation that uses a static JWKSet instead of fetching from a URL.
   * This allows tests to run without an HTTP server for JWKS.
   */
  private static class TestJwtTokenVerifier extends JwtTokenVerifier {

    private final JWKSet jwkSet;

    TestJwtTokenVerifier(JWKSet jwkSet) {
      super("http://localhost/.well-known/jwks.json", "");
      this.jwkSet = jwkSet;
    }

    @Override
    public JwtClaims validateAndGetClaims(String token) {
      try {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> keySelector =
            new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwkSet));
        processor.setJWSKeySelector(keySelector);

        JWTClaimsSet claimsSet = processor.process(token, null);
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
