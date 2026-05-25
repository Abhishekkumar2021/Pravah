package io.pravah.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Checks JWT revocation blocklist in Redis (ADR-012).
 *
 * <p>When a token is revoked (logout, suspicious activity), its JTI is added to Redis with TTL
 * matching the token's remaining validity. This filter rejects requests with blocklisted tokens.
 */
@Component
public class JwtBlocklistChecker {

  private static final Logger log = LoggerFactory.getLogger(JwtBlocklistChecker.class);
  private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

  private final ReactiveStringRedisTemplate redisTemplate;

  public JwtBlocklistChecker(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Check if a JWT is blocklisted (revoked).
   *
   * @param jti the JWT ID (jti claim)
   * @return true if blocklisted or if the check cannot be completed (fail closed per ADR-012)
   */
  public Mono<Boolean> isBlocklisted(String jti) {
    if (jti == null || jti.isBlank()) {
      return Mono.just(false);
    }

    return redisTemplate
        .hasKey(BLOCKLIST_PREFIX + jti)
        .doOnError(e -> log.warn("JWT blocklist check failed for jti={}: {}", jti, e.getMessage()))
        .onErrorReturn(true);
  }

  /**
   * Add a JWT to the blocklist (revoke it).
   *
   * @param jti the JWT ID
   * @param ttlSeconds time until token expires (blocklist entry auto-expires with token)
   * @return completion signal
   */
  public Mono<Boolean> addToBlocklist(String jti, long ttlSeconds) {
    if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
      return Mono.just(false);
    }

    return redisTemplate
        .opsForValue()
        .set(BLOCKLIST_PREFIX + jti, "1", java.time.Duration.ofSeconds(ttlSeconds))
        .doOnSuccess(v -> log.info("Added JWT to blocklist: jti={}, ttl={}s", jti, ttlSeconds))
        .thenReturn(true)
        .onErrorReturn(false);
  }
}
