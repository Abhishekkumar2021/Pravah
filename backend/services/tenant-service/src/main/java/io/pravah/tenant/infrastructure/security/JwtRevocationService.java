package io.pravah.tenant.infrastructure.security;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Revokes JWTs via Redis blocklist (ADR-012), shared prefix with gateway. */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class JwtRevocationService {

  private static final Logger log = LoggerFactory.getLogger(JwtRevocationService.class);
  private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

  private final StringRedisTemplate redisTemplate;

  public JwtRevocationService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void revoke(String jti, Instant expiresAt) {
    if (jti == null || jti.isBlank()) {
      return;
    }
    long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
    if (ttlSeconds <= 0) {
      return;
    }
    redisTemplate.opsForValue().set(BLOCKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    log.info("Revoked JWT jti={} ttlSeconds={}", jti, ttlSeconds);
  }
}
