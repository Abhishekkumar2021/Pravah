package io.pravah.pipeline.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates JWTs issued by tenant-service (shared secret per ADR-009).
 *
 * <p>Also supports generating tokens in tests via {@link #generateAccessToken}.
 */
@Component
public class JwtTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
  private static final String CLAIM_TENANT_ID = "tenant_id";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_NAME = "name";

  private final SecretKey secretKey;
  private final long tokenValidityMs;
  private final long refreshTokenValidityMs;

  public JwtTokenProvider(
      @Value("${pravah.security.jwt.secret}") String secret,
      @Value("${pravah.security.jwt.expiration-ms:86400000}") long tokenValidityMs,
      @Value("${pravah.security.jwt.refresh-expiration-ms:604800000}")
          long refreshTokenValidityMs) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.tokenValidityMs = tokenValidityMs;
    this.refreshTokenValidityMs = refreshTokenValidityMs;
  }

  public String generateAccessToken(UUID userId, UUID tenantId, String email, String name) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(tokenValidityMs);
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_TENANT_ID, tenantId.toString())
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_NAME, name)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public String generateRefreshToken(UUID userId) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(refreshTokenValidityMs);
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public Claims validateAndGetClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public UUID getUserIdFromClaims(Claims claims) {
    return UUID.fromString(claims.getSubject());
  }

  public UUID getTenantIdFromClaims(Claims claims) {
    String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
    return tenantId != null ? UUID.fromString(tenantId) : null;
  }

  public UUID getUserId(String token) {
    return getUserIdFromClaims(validateAndGetClaims(token));
  }

  public UUID getTenantId(String token) {
    return getTenantIdFromClaims(validateAndGetClaims(token));
  }

  public boolean isValidToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      validateAndGetClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.debug("JWT token expired: {}", e.getMessage());
      return false;
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Invalid JWT token: {}", e.getMessage());
      return false;
    }
  }
}
