package io.pravah.common.domain.resolution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for value resolution, providing access to tenant, execution, and cached values.
 *
 * <p>This is an immutable record except for the cache maps which are intentionally mutable to allow
 * caching resolved values within a single execution context.
 *
 * @param tenantId the current tenant ID (required for secret lookups)
 * @param executionId the current execution ID (may be null during validation)
 * @param executionTime the execution start time
 * @param variableContext resolved variable values (populated at execution start)
 * @param secretsCache cache to avoid repeated secret lookups within same execution (mutable)
 * @param stageOutputCache cache to avoid repeated stage output lookups within same stage (mutable)
 */
public record ResolutionContext(
    UUID tenantId,
    UUID executionId,
    Instant executionTime,
    Map<String, Object> variableContext,
    Map<String, String> secretsCache,
    Map<String, Object> stageOutputCache) {

  public ResolutionContext {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId is required for resolution context");
    }
    variableContext = variableContext != null ? Map.copyOf(variableContext) : Map.of();
    secretsCache = secretsCache != null ? secretsCache : new ConcurrentHashMap<>();
    stageOutputCache = stageOutputCache != null ? stageOutputCache : new ConcurrentHashMap<>();
  }

  /** Creates a context for validation (no execution yet). */
  public static ResolutionContext forValidation(UUID tenantId) {
    return new ResolutionContext(
        tenantId, null, null, Map.of(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
  }

  /** Creates a context for execution start (variables resolved, secrets/outputs deferred). */
  public static ResolutionContext forExecution(
      UUID tenantId, UUID executionId, Instant executionTime, Map<String, Object> variableContext) {
    return new ResolutionContext(
        tenantId,
        executionId,
        executionTime,
        variableContext,
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>());
  }
}
