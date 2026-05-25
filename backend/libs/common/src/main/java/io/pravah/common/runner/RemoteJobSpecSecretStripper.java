package io.pravah.common.runner;

import io.pravah.common.domain.resolution.ValueReferenceParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strips resolved secret values from remote job specs before gRPC dispatch.
 *
 * <p>Env keys whose raw config referenced {@code ${secret.name}} are moved to {@code
 * secretEnvironment}; sensitive runtime keys (e.g. SQL password) use reserved names resolved by
 * execution-service at job start.
 */
public final class RemoteJobSpecSecretStripper {

  /** Reserved secret name resolved to JDBC password for SQL stages. */
  public static final String RUNTIME_SQL_PASSWORD = "__runtime/sql-password";

  private static final Set<String> ALWAYS_STRIP_ENV_KEYS = Set.of(RemoteJobSpecEnv.SQL_PASSWORD);

  private RemoteJobSpecSecretStripper() {}

  public static RemoteJobSpecPayload stripSecrets(
      RemoteJobSpecPayload payload, Map<String, Object> rawStageConfig) {
    Map<String, String> secretEnvironment = new LinkedHashMap<>();
    Map<String, String> environment = new LinkedHashMap<>(payload.environment());

    extractSecretRefsFromRawEnv(rawStageConfig, secretEnvironment);
    for (String key : ALWAYS_STRIP_ENV_KEYS) {
      if (environment.containsKey(key)) {
        environment.remove(key);
        secretEnvironment.putIfAbsent(key, RUNTIME_SQL_PASSWORD);
      }
    }
    for (Map.Entry<String, String> entry : secretEnvironment.entrySet()) {
      environment.remove(entry.getKey());
    }

    if (secretEnvironment.isEmpty()) {
      return payload;
    }
    return new RemoteJobSpecPayload(
        payload.executor(),
        payload.image(),
        payload.commands(),
        Map.copyOf(environment),
        payload.timeoutSeconds(),
        payload.memoryBytes(),
        payload.cpuCores(),
        Map.copyOf(secretEnvironment));
  }

  @SuppressWarnings("unchecked")
  private static void extractSecretRefsFromRawEnv(
      Map<String, Object> rawStageConfig, Map<String, String> secretEnvironment) {
    if (rawStageConfig == null) {
      return;
    }
    Object rawEnv = rawStageConfig.get("env");
    if (!(rawEnv instanceof Map<?, ?> envMap)) {
      return;
    }
    for (Map.Entry<?, ?> entry : envMap.entrySet()) {
      if (!(entry.getValue() instanceof String rawValue)) {
        continue;
      }
      List<String> secretNames = ValueReferenceParser.extractSecretNames(rawValue);
      if (!secretNames.isEmpty()) {
        secretEnvironment.put(entry.getKey().toString(), secretNames.get(0));
      }
    }
  }
}
