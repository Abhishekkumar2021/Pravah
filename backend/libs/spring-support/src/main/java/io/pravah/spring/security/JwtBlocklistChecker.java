package io.pravah.spring.security;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Checks JWT revocation blocklist in Redis (ADR-012). Mirrors gateway {@code JwtBlocklistChecker}.
 *
 * <p>Uses reflection so services without {@code spring-boot-starter-data-redis} on the classpath
 * can still scan {@code io.pravah.spring.security} (e.g. pipeline-service).
 */
@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnBean(name = "stringRedisTemplate")
public class JwtBlocklistChecker {

  private static final Logger log = LoggerFactory.getLogger(JwtBlocklistChecker.class);
  private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

  private final Object redisTemplate;

  public JwtBlocklistChecker(@Qualifier("stringRedisTemplate") Object redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isBlocklisted(String jti) {
    if (jti == null || jti.isBlank()) {
      return false;
    }
    try {
      Method hasKey = redisTemplate.getClass().getMethod("hasKey", Object.class);
      Boolean exists = (Boolean) hasKey.invoke(redisTemplate, BLOCKLIST_PREFIX + jti);
      return Boolean.TRUE.equals(exists);
    } catch (Exception e) {
      log.warn("JWT blocklist check failed for jti={}: {}", jti, e.getMessage());
      return false;
    }
  }
}
