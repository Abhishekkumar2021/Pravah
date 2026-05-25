package io.pravah.tenant.infrastructure.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Local JWT verifier for tenant-service that uses the JwtTokenIssuer's JWKS directly.
 *
 * <p>Since tenant-service is both the issuer and a consumer of JWTs, it cannot use the HTTP-based
 * JwtTokenVerifier (that would create a circular dependency at startup). This verifier gets the
 * JWKS directly from the local JwtTokenIssuer.
 */
@Component
@Primary
public class LocalJwtTokenVerifier extends JwtTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(LocalJwtTokenVerifier.class);

  private final JwtTokenIssuer jwtTokenIssuer;

  public LocalJwtTokenVerifier(
      JwtTokenIssuer jwtTokenIssuer,
      @org.springframework.beans.factory.annotation.Value("${pravah.security.jwt.issuer:}")
          String issuer) {
    super("local://in-process-jwks", issuer);
    this.jwtTokenIssuer = jwtTokenIssuer;
  }

  /** Tenant-service is the issuer; keys are in-process — no HTTP JWKS polling. */
  @Override
  public void scheduledJwksRefresh() {
    // Intentionally empty — avoids erroneous refresh to http://localhost/ from legacy placeholder
    // URL.
  }

  @Override
  public JwtClaims validateAndGetClaims(String token) {
    try {
      ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(
              JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwtTokenIssuer.getJwks()));
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
