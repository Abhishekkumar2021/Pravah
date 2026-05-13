package io.pravah.tenant.infrastructure.security;

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
 * JWT token provider for authentication.
 *
 * <p>Generates and validates JWT tokens containing user identity and tenant context. Per ADR-009
 * and LLD sequence diagram (User Authentication), tokens contain: - sub: user_id - tenant_id:
 * tenant UUID - exp: expiration time
 *
 * @see <a href="docs/adr/ADR-009-jwt-oauth2-authentication.md">ADR-009: JWT Authentication</a>
 * @see <a href="docs/lld/04-sequence-diagrams.md">LLD: User Authentication</a>
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

  /**
   * Generates an access token for the authenticated user.
   *
   * @param userId the user's UUID
   * @param tenantId the user's tenant UUID
   * @param email the user's email
   * @param name the user's display name
   * @return signed JWT token string
   */
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

  /**
   * Generates a refresh token for token renewal.
   *
   * @param userId the user's UUID
   * @return signed refresh token string
   */
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

  /**
   * Validates a token and extracts claims.
   *
   * @param token the JWT token string
   * @return parsed claims if valid
   * @throws JwtException if token is invalid or expired
   */
  public Claims validateAndGetClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  /**
   * Extracts user ID from token.
   *
   * @param token the JWT token
   * @return user UUID
   */
  public UUID getUserId(String token) {
    Claims claims = validateAndGetClaims(token);
    return UUID.fromString(claims.getSubject());
  }

  /**
   * Extracts tenant ID from token.
   *
   * @param token the JWT token
   * @return tenant UUID
   */
  public UUID getTenantId(String token) {
    Claims claims = validateAndGetClaims(token);
    String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
    return tenantId != null ? UUID.fromString(tenantId) : null;
  }

  /**
   * Checks if a token is valid (not expired, properly signed).
   *
   * @param token the JWT token
   * @return true if valid
   */
  public boolean isValidToken(String token) {
    try {
      validateAndGetClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.debug("JWT token expired: {}", e.getMessage());
      return false;
    } catch (JwtException e) {
      log.debug("Invalid JWT token: {}", e.getMessage());
      return false;
    }
  }
}
