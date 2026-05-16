package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalJwtTokenVerifierTest {

  private static final String ISSUER = "pravah-test";

  private JwtTokenIssuer issuer;
  private LocalJwtTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    issuer = new JwtTokenIssuer(ISSUER);
    issuer.initializeKey();
    verifier = new LocalJwtTokenVerifier(issuer);
  }

  @Test
  void validateAndGetClaims_returnsUserAndTenant() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token =
        issuer.generateAccessToken(
            userId, tenantId, List.of("editor"), List.of("users:read", "pipelines:write"));

    var claims = verifier.validateAndGetClaims(token);

    assertThat(claims.userId()).isEqualTo(userId);
    assertThat(claims.tenantId()).isEqualTo(tenantId);
    assertThat(claims.permissions()).contains("users:read");
  }

  @Test
  void isValidToken_rejectsMalformedToken() {
    assertThat(verifier.isValidToken("not-a-jwt")).isFalse();
    assertThat(verifier.isValidToken(null)).isFalse();
    assertThat(verifier.isValidToken("   ")).isFalse();
  }

  @Test
  void getUserIdAndTenantId_extractFromValidToken() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = issuer.generateAccessToken(userId, tenantId);

    assertThat(verifier.getUserId(token)).isEqualTo(userId);
    assertThat(verifier.getTenantId(token)).isEqualTo(tenantId);
  }

  @Test
  void validateAndGetClaims_rejectsTamperedToken() {
    String token = issuer.generateAccessToken(UUID.randomUUID(), UUID.randomUUID());
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThatThrownBy(() -> verifier.validateAndGetClaims(tampered))
        .isInstanceOf(JwtVerificationException.class);
  }
}
