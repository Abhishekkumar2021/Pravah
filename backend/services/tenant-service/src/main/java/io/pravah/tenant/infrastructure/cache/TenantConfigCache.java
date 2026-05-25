package io.pravah.tenant.infrastructure.cache;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.tenant.domain.model.Tenant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-based cache for tenant configuration per ADR-012.
 *
 * <p>Caches tenant configuration (rate limits, feature flags, settings) with a 10-minute TTL.
 * Invalidation happens on tenant updates via explicit cache eviction.
 *
 * <p>Pattern: Cache-aside — application fetches from cache; on miss, fetches from DB and populates
 * cache.
 */
@Component
@ConditionalOnProperty(name = "pravah.cache.enabled", havingValue = "true", matchIfMissing = true)
public class TenantConfigCache {

  private static final Logger log = LoggerFactory.getLogger(TenantConfigCache.class);
  private static final String KEY_PREFIX = "tenant:config:";
  private static final Duration TTL = Duration.ofMinutes(10);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public TenantConfigCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Get cached tenant configuration.
   *
   * @param tenantId the tenant ID
   * @return cached config if present, empty otherwise
   */
  public Optional<CachedTenantConfig> get(UUID tenantId) {
    try {
      String json = redisTemplate.opsForValue().get(KEY_PREFIX + tenantId);
      if (json == null) {
        log.debug("Cache miss for tenant", kv("tenant_id", tenantId));
        return Optional.empty();
      }
      log.debug("Cache hit for tenant", kv("tenant_id", tenantId));
      return Optional.of(objectMapper.readValue(json, CachedTenantConfig.class));
    } catch (Exception e) {
      log.warn(
          "Cache read failed for tenant", kv("tenant_id", tenantId), kv("error", e.getMessage()));
      return Optional.empty();
    }
  }

  /**
   * Cache a tenant configuration.
   *
   * @param tenant the tenant entity
   */
  public void put(Tenant tenant) {
    try {
      CachedTenantConfig config = CachedTenantConfig.from(tenant);
      String json = objectMapper.writeValueAsString(config);
      redisTemplate.opsForValue().set(KEY_PREFIX + tenant.getId(), json, TTL);
      log.debug("Cached tenant config", kv("tenant_id", tenant.getId()));
    } catch (JsonProcessingException e) {
      log.warn(
          "Cache write failed for tenant",
          kv("tenant_id", tenant.getId()),
          kv("error", e.getMessage()));
    }
  }

  /**
   * Invalidate cached tenant configuration.
   *
   * <p>Call this whenever tenant settings are updated.
   *
   * @param tenantId the tenant ID
   */
  public void invalidate(UUID tenantId) {
    Boolean deleted = redisTemplate.delete(KEY_PREFIX + tenantId);
    if (Boolean.TRUE.equals(deleted)) {
      log.debug("Invalidated tenant cache", kv("tenant_id", tenantId));
    }
  }

  /**
   * Cached tenant configuration DTO.
   *
   * <p>Contains tenant settings used for rate limiting, feature flags, and quota enforcement.
   */
  public record CachedTenantConfig(
      UUID id,
      String name,
      String tier,
      int rateLimitPerSecond,
      int burstCapacity,
      String settings) {

    public static CachedTenantConfig from(Tenant tenant) {
      int rateLimit = rateLimitForTier(tenant.getTier());
      int burst = burstCapacityForTier(tenant.getTier());
      return new CachedTenantConfig(
          tenant.getId(),
          tenant.getName(),
          tenant.getTier().name(),
          rateLimit,
          burst,
          tenant.getSettings());
    }

    private static int rateLimitForTier(Tenant.Tier tier) {
      return switch (tier) {
        case FREE -> 50;
        case TEAM -> 200;
        case ENTERPRISE -> 1000;
      };
    }

    private static int burstCapacityForTier(Tenant.Tier tier) {
      return switch (tier) {
        case FREE -> 100;
        case TEAM -> 400;
        case ENTERPRISE -> 2000;
      };
    }
  }
}
