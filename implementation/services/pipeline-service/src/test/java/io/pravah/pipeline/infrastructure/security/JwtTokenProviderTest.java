package io.pravah.pipeline.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET =
      "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
  private static final long TOKEN_VALIDITY_MS = 3600000; // 1 hour
  private static final long REFRESH_TOKEN_VALIDITY_MS = 86400000; // 1 day

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(SECRET, TOKEN_VALIDITY_MS, REFRESH_TOKEN_VALIDITY_MS);
  }

  @Test
  void generateAccessToken_containsAllClaims() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String email = "test@example.com";
    String name = "Test User";

    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, email, name);

    assertThat(token).isNotBlank();

    Claims claims = jwtTokenProvider.validateAndGetClaims(token);
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
    assertThat(claims.get("email", String.class)).isEqualTo(email);
    assertThat(claims.get("name", String.class)).isEqualTo(name);
    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.getExpiration()).isAfter(new Date());
  }

  @Test
  void generateRefreshToken_containsUserIdOnly() {
    UUID userId = UUID.randomUUID();

    String token = jwtTokenProvider.generateRefreshToken(userId);

    assertThat(token).isNotBlank();

    Claims claims = jwtTokenProvider.validateAndGetClaims(token);
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.get("tenant_id")).isNull();
    assertThat(claims.getExpiration()).isAfter(new Date());
  }

  @Test
  void getUserId_extractsCorrectly() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    UUID extractedUserId = jwtTokenProvider.getUserId(token);

    assertThat(extractedUserId).isEqualTo(userId);
  }

  @Test
  void getTenantId_extractsCorrectly() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    UUID extractedTenantId = jwtTokenProvider.getTenantId(token);

    assertThat(extractedTenantId).isEqualTo(tenantId);
  }

  @Test
  void getUserIdFromClaims_extractsCorrectly() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");
    Claims claims = jwtTokenProvider.validateAndGetClaims(token);

    UUID extractedUserId = jwtTokenProvider.getUserIdFromClaims(claims);

    assertThat(extractedUserId).isEqualTo(userId);
  }

  @Test
  void getTenantIdFromClaims_extractsCorrectly() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");
    Claims claims = jwtTokenProvider.validateAndGetClaims(token);

    UUID extractedTenantId = jwtTokenProvider.getTenantIdFromClaims(claims);

    assertThat(extractedTenantId).isEqualTo(tenantId);
  }

  @Test
  void getTenantIdFromClaims_nullWhenMissing() {
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateRefreshToken(userId);
    Claims claims = jwtTokenProvider.validateAndGetClaims(token);

    UUID extractedTenantId = jwtTokenProvider.getTenantIdFromClaims(claims);

    assertThat(extractedTenantId).isNull();
  }

  @Test
  void isValidToken_trueForValidToken() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    assertThat(jwtTokenProvider.isValidToken(token)).isTrue();
  }

  @Test
  void isValidToken_falseForExpiredToken() {
    JwtTokenProvider shortLivedProvider =
        new JwtTokenProvider(SECRET, 1, REFRESH_TOKEN_VALIDITY_MS);
    String token =
        shortLivedProvider.generateAccessToken(
            UUID.randomUUID(), UUID.randomUUID(), "e@e.com", "Name");

    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertThat(jwtTokenProvider.isValidToken(token)).isFalse();
  }

  @Test
  void isValidToken_falseForInvalidSignature() {
    String differentSecret = "different-secret-key-that-is-at-least-256-bits-long-for-hs256-algo";
    JwtTokenProvider otherProvider =
        new JwtTokenProvider(differentSecret, TOKEN_VALIDITY_MS, REFRESH_TOKEN_VALIDITY_MS);
    String token =
        otherProvider.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), "e@e.com", "Name");

    assertThat(jwtTokenProvider.isValidToken(token)).isFalse();
  }

  @Test
  void isValidToken_falseForMalformedToken() {
    assertThat(jwtTokenProvider.isValidToken("not.a.valid.token")).isFalse();
    assertThat(jwtTokenProvider.isValidToken("")).isFalse();
    assertThat(jwtTokenProvider.isValidToken("random-string")).isFalse();
  }

  @Test
  void validateAndGetClaims_throwsForExpiredToken() {
    var secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String expiredToken =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(secretKey)
            .compact();

    assertThatThrownBy(() -> jwtTokenProvider.validateAndGetClaims(expiredToken))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void validateAndGetClaims_throwsForInvalidSignature() {
    String differentSecret = "different-secret-key-that-is-at-least-256-bits-long-for-hs256-algo";
    JwtTokenProvider otherProvider =
        new JwtTokenProvider(differentSecret, TOKEN_VALIDITY_MS, REFRESH_TOKEN_VALIDITY_MS);
    String token =
        otherProvider.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), "e@e.com", "Name");

    assertThatThrownBy(() -> jwtTokenProvider.validateAndGetClaims(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void validateAndGetClaims_throwsForMalformedToken() {
    assertThatThrownBy(() -> jwtTokenProvider.validateAndGetClaims("malformed"))
        .isInstanceOf(JwtException.class);
  }
}
