package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.JWTClaimsSet;
import io.pravah.common.security.JwtClaimNames;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenVerifierClaimsTest {

  private final TestJwtTokenVerifier verifier = new TestJwtTokenVerifier();

  @Test
  void extractClaims_parsesRolesAndPermissions() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim(JwtClaimNames.TENANT_ID, tenantId.toString())
            .claim(JwtClaimNames.ROLES, List.of("editor"))
            .claim(JwtClaimNames.PERMISSIONS, List.of("pipelines:read"))
            .jwtID("jti-1")
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(900)))
            .build();

    var parsed = verifier.extractClaims(claims);

    assertThat(parsed.userId()).isEqualTo(userId);
    assertThat(parsed.tenantId()).isEqualTo(tenantId);
    assertThat(parsed.roles()).containsExactly("editor");
    assertThat(parsed.permissions()).containsExactly("pipelines:read");
  }

  @Test
  void extractClaims_missingSubject_throws() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .claim(JwtClaimNames.TENANT_ID, UUID.randomUUID().toString())
            .build();

    assertThatThrownBy(() -> verifier.extractClaims(claims))
        .isInstanceOf(JwtVerificationException.class)
        .hasMessageContaining("subject");
  }

  @Test
  void extractClaims_missingTenantId_throws() {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString()).build();

    assertThatThrownBy(() -> verifier.extractClaims(claims))
        .isInstanceOf(JwtVerificationException.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void isValidToken_blankIsFalse() {
    assertThat(verifier.isValidToken(null)).isFalse();
    assertThat(verifier.isValidToken("  ")).isFalse();
  }

  private static final class TestJwtTokenVerifier extends JwtTokenVerifier {
    TestJwtTokenVerifier() {
      super("http://localhost/jwks.json");
    }
  }
}
