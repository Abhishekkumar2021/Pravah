package io.pravah.execution.application;

import io.pravah.common.runner.RemoteJobSpecSecretStripper;
import io.pravah.common.security.SecretNameValidator;
import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.execution.application.port.SecretCatalog;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves secret environment entries for remote runners at job execution time. */
@Service
public class JobEnvironmentSecretService {

  private final JobEntityRepository jobEntityRepository;
  private final ExecutionEntityRepository executionEntityRepository;
  private final SecretCatalog secretCatalog;
  private final ConnectionCatalog connectionCatalog;

  public JobEnvironmentSecretService(
      JobEntityRepository jobEntityRepository,
      ExecutionEntityRepository executionEntityRepository,
      SecretCatalog secretCatalog,
      ConnectionCatalog connectionCatalog) {
    this.jobEntityRepository = jobEntityRepository;
    this.executionEntityRepository = executionEntityRepository;
    this.secretCatalog = secretCatalog;
    this.connectionCatalog = connectionCatalog;
  }

  @Transactional(readOnly = true)
  public Map<String, String> resolve(
      UUID tenantId, UUID executionId, UUID jobId, Map<String, String> secretEnvironment) {
    if (secretEnvironment == null || secretEnvironment.isEmpty()) {
      return Map.of();
    }
    JobEntity job =
        jobEntityRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    if (!job.getExecutionId().equals(executionId)) {
      throw new IllegalArgumentException("Job does not belong to execution");
    }
    ExecutionEntity execution =
        executionEntityRepository
            .findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
    if (!execution.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Execution tenant mismatch");
    }

    Instant executionTime =
        execution.getStartedAt() != null ? execution.getStartedAt() : Instant.now();
    Map<String, Object> stageConfig =
        findStageConfig(execution.getDefinitionSnapshot(), job.getStageId());

    Map<String, String> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : secretEnvironment.entrySet()) {
      String envKey = entry.getKey();
      String secretName = entry.getValue();
      if (RemoteJobSpecSecretStripper.RUNTIME_SQL_PASSWORD.equals(secretName)) {
        resolved.put(envKey, resolveSqlPassword(stageConfig, execution));
      } else {
        SecretNameValidator.requireValidTenantSecretName(secretName);
        resolved.put(
            envKey, secretCatalog.resolve(tenantId, executionId, executionTime, secretName));
      }
    }
    return Map.copyOf(resolved);
  }

  private String resolveSqlPassword(Map<String, Object> stageConfig, ExecutionEntity execution) {
    Map<String, Object> rawConfig = extractConfig(stageConfig);
    ResolvedJdbcConnection jdbc = resolveJdbcConnection(rawConfig, execution);
    return jdbc.password() != null ? jdbc.password() : "";
  }

  private ResolvedJdbcConnection resolveJdbcConnection(
      Map<String, Object> stageConfig, ExecutionEntity execution) {
    Object connObj = stageConfig.get("connection");
    if (connObj instanceof String connectionName && !connectionName.isBlank()) {
      return connectionCatalog.resolve(execution.getTenantId(), connectionName.trim());
    }
    if (connObj instanceof Map<?, ?> connMap) {
      String url = stringField(connMap, "url");
      if (url != null && !url.isBlank()) {
        return new ResolvedJdbcConnection(
            "inline",
            url,
            stringField(connMap, "username") != null ? stringField(connMap, "username") : "",
            stringField(connMap, "password") != null ? stringField(connMap, "password") : "");
      }
    }
    throw new IllegalArgumentException("SQL stage missing connection configuration");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> findStageConfig(
      Map<String, Object> definitionSnapshot, String stageId) {
    if (definitionSnapshot == null) {
      throw new IllegalArgumentException("Missing pipeline definition snapshot");
    }
    Object stagesObj = definitionSnapshot.get("stages");
    if (!(stagesObj instanceof List<?> stages)) {
      throw new IllegalArgumentException("Missing stages in definition snapshot");
    }
    for (Object stageObj : stages) {
      if (stageObj instanceof Map<?, ?> stageMap) {
        Object id = stageMap.get("id");
        if (id != null && stageId.equals(id.toString())) {
          return (Map<String, Object>) stageMap;
        }
      }
    }
    throw new IllegalArgumentException("Stage not found in snapshot: " + stageId);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractConfig(Map<String, Object> stageDefinition) {
    Object config = stageDefinition.get("config");
    if (config instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  private static String stringField(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }
}
