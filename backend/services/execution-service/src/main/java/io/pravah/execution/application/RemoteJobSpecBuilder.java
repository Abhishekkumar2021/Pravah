package io.pravah.execution.application;

import io.pravah.common.domain.ContainerResourceParser;
import io.pravah.common.domain.ResourceProfiles;
import io.pravah.common.domain.StageTimeout;
import io.pravah.common.domain.StageTimeoutParser;
import io.pravah.common.runner.RemoteJobSpecEnv;
import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds {@link RemoteJobSpecPayload} from pipeline stage definitions for remote runner dispatch.
 */
@Component
public class RemoteJobSpecBuilder {

  private final ExecutionStageConfigResolver configResolver;
  private final ConnectionCatalog connectionCatalog;

  public RemoteJobSpecBuilder(
      ExecutionStageConfigResolver configResolver, ConnectionCatalog connectionCatalog) {
    this.configResolver = configResolver;
    this.connectionCatalog = connectionCatalog;
  }

  public RemoteJobSpecPayload build(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {
    if (stageDefinition == null) {
      throw new IllegalArgumentException("Stage definition is required for remote job spec");
    }
    String stageType = resolveStageType(stageDefinition);
    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      throw new IllegalArgumentException("Remote stage missing config section");
    }
    Map<String, Object> resolved = configResolver.resolveConfig(execution, rawConfig);
    StageTimeout timeout =
        StageTimeoutParser.resolveForStage(execution.getDefinitionSnapshot(), job.getStageId());
    long timeoutSeconds =
        timeout.isConfigured()
            ? timeout.timeoutSeconds()
            : RemoteJobSpecPayload.DEFAULT_TIMEOUT_SECONDS;
    ResourceProfiles.ResourceLimits limits = parseResourceLimits(resolved);

    return switch (stageType) {
      case "container" -> buildContainer(resolved, timeoutSeconds, limits);
      case "python" -> buildPython(resolved, timeoutSeconds, limits);
      case "sql" -> buildSql(resolved, execution, timeoutSeconds, limits);
      case "dbt" -> buildDbt(resolved, timeoutSeconds, limits);
      case "spark" -> buildSpark(resolved, timeoutSeconds, limits);
      default ->
          throw new IllegalArgumentException(
              "Stage type '%s' is not supported on remote runners".formatted(stageType));
    };
  }

  private RemoteJobSpecPayload buildContainer(
      Map<String, Object> config, long timeoutSeconds, ResourceProfiles.ResourceLimits limits) {
    String image = requireNonBlank(config, "image", "Container stage requires config.image");
    List<String> command = parseCommand(config.get("command"));
    Map<String, String> env = parseEnv(config.get("env"));
    return new RemoteJobSpecPayload(
        "container",
        image.trim(),
        command,
        env,
        timeoutSeconds,
        memoryBytes(limits),
        cpuCores(limits));
  }

  private RemoteJobSpecPayload buildPython(
      Map<String, Object> config, long timeoutSeconds, ResourceProfiles.ResourceLimits limits) {
    Map<String, String> env = new LinkedHashMap<>(parseEnv(config.get("env")));
    String script = getString(config, "script");
    if (script == null || script.isBlank()) {
      throw new IllegalArgumentException(
          "Python stage requires config.script for remote execution");
    }
    env.put(RemoteJobSpecEnv.PYTHON_SCRIPT, script);
    List<?> requirements = parseRequirementsList(config.get("requirements"));
    if (!requirements.isEmpty()) {
      env.put(
          RemoteJobSpecEnv.PYTHON_REQUIREMENTS,
          String.join("\n", requirements.stream().map(Object::toString).toList()));
    }
    return new RemoteJobSpecPayload(
        "python", null, List.of(), env, timeoutSeconds, memoryBytes(limits), cpuCores(limits));
  }

  private RemoteJobSpecPayload buildSql(
      Map<String, Object> config,
      ExecutionEntity execution,
      long timeoutSeconds,
      ResourceProfiles.ResourceLimits limits) {
    String query = requireNonBlank(config, "query", "SQL stage requires config.query");
    ResolvedJdbcConnection jdbc = resolveJdbcConnection(config, execution);
    Map<String, String> env = new LinkedHashMap<>();
    env.put(RemoteJobSpecEnv.SQL_JDBC_URL, jdbc.jdbcUrl());
    env.put(RemoteJobSpecEnv.SQL_USER, jdbc.username() != null ? jdbc.username() : "");
    env.put(RemoteJobSpecEnv.SQL_PASSWORD, jdbc.password() != null ? jdbc.password() : "");
    env.put(RemoteJobSpecEnv.SQL_QUERY, query);
    return new RemoteJobSpecPayload(
        "sql", null, List.of(), env, timeoutSeconds, memoryBytes(limits), cpuCores(limits));
  }

  private RemoteJobSpecPayload buildDbt(
      Map<String, Object> config, long timeoutSeconds, ResourceProfiles.ResourceLimits limits) {
    String projectDir =
        requireNonBlank(config, "project_dir", "dbt stage requires config.project_dir");
    String dbtBinary = getString(config, "dbt_binary");
    if (dbtBinary == null || dbtBinary.isBlank()) {
      dbtBinary = "dbt";
    }
    StringBuilder shell = new StringBuilder();
    shell
        .append("cd ")
        .append(shellQuote(projectDir.trim()))
        .append(" && ")
        .append(dbtBinary)
        .append(" run");
    String select = getString(config, "select");
    if (select != null && !select.isBlank()) {
      shell.append(" --select ").append(shellQuote(select.trim()));
    }
    String target = getString(config, "target");
    if (target != null && !target.isBlank()) {
      shell.append(" --target ").append(shellQuote(target.trim()));
    }
    return new RemoteJobSpecPayload(
        "shell",
        null,
        List.of("sh", "-c", shell.toString()),
        parseEnv(config.get("env")),
        timeoutSeconds,
        memoryBytes(limits),
        cpuCores(limits));
  }

  private RemoteJobSpecPayload buildSpark(
      Map<String, Object> config, long timeoutSeconds, ResourceProfiles.ResourceLimits limits) {
    String appJar = requireNonBlank(config, "app_jar", "spark stage requires config.app_jar");
    String mainClass =
        requireNonBlank(config, "main_class", "spark stage requires config.main_class");
    String sparkSubmit =
        getString(config, "spark_submit") != null
            ? getString(config, "spark_submit")
            : "spark-submit";
    List<String> command = new ArrayList<>();
    command.add(sparkSubmit);
    command.add("--master");
    command.add(getString(config, "master") != null ? getString(config, "master") : "local[*]");
    command.add("--deploy-mode");
    command.add(
        getString(config, "deploy_mode") != null ? getString(config, "deploy_mode") : "client");
    command.add("--class");
    command.add(mainClass.trim());
    command.add(appJar.trim());
    Object args = config.get("args");
    if (args instanceof List<?> list) {
      for (Object item : list) {
        if (item != null) {
          command.add(item.toString());
        }
      }
    }
    return new RemoteJobSpecPayload(
        "shell",
        null,
        command,
        parseEnv(config.get("env")),
        timeoutSeconds,
        memoryBytes(limits),
        cpuCores(limits));
  }

  private RemoteJobSpecPayload buildShellFallback(
      String executor,
      Map<String, Object> config,
      long timeoutSeconds,
      ResourceProfiles.ResourceLimits limits,
      List<String> defaultCommand) {
    List<String> command = parseCommand(config.get("command"));
    if (command.isEmpty()) {
      command = defaultCommand;
    }
    return new RemoteJobSpecPayload(
        executor,
        null,
        command,
        parseEnv(config.get("env")),
        timeoutSeconds,
        memoryBytes(limits),
        cpuCores(limits));
  }

  private ResolvedJdbcConnection resolveJdbcConnection(
      Map<String, Object> stageConfig, ExecutionEntity execution) {
    Object connObj = stageConfig.get("connection");
    if (connObj instanceof String connectionName && !connectionName.isBlank()) {
      return connectionCatalog.resolve(execution.getTenantId(), connectionName.trim());
    }
    if (connObj instanceof Map<?, ?> connMap) {
      String url = getString(connMap, "url");
      if (url != null && !url.isBlank()) {
        return new ResolvedJdbcConnection(
            "inline",
            url,
            getString(connMap, "username") != null ? getString(connMap, "username") : "",
            getString(connMap, "password") != null ? getString(connMap, "password") : "");
      }
    }
    throw new IllegalArgumentException(
        "SQL stage requires config.connection (named connection or inline JDBC url)");
  }

  private static Long memoryBytes(ResourceProfiles.ResourceLimits limits) {
    if (limits.memory() == null || limits.memory().isBlank()) {
      return null;
    }
    return ContainerResourceParser.parseMemoryBytes(limits.memory());
  }

  private static Double cpuCores(ResourceProfiles.ResourceLimits limits) {
    if (limits.cpus() == null || limits.cpus().isBlank()) {
      return null;
    }
    return Double.parseDouble(ContainerResourceParser.toDockerCpuLimit(limits.cpus()));
  }

  private static ResourceProfiles.ResourceLimits parseResourceLimits(Map<String, Object> config) {
    Object resources = config.get("resources");
    if (!(resources instanceof Map<?, ?> map)) {
      return new ResourceProfiles.ResourceLimits(null, null);
    }
    return ResourceProfiles.mergeWithProfile(map);
  }

  private static List<String> parseCommand(Object commandObj) {
    if (commandObj == null) {
      return List.of();
    }
    if (commandObj instanceof String cmd) {
      return List.of(cmd);
    }
    if (commandObj instanceof List<?> list) {
      List<String> command = new ArrayList<>();
      for (Object item : list) {
        if (item != null) {
          command.add(item.toString());
        }
      }
      return command;
    }
    throw new IllegalArgumentException("command must be a string or list of strings");
  }

  private static List<String> parseRequirementsList(Object requirementsObj) {
    if (requirementsObj == null) {
      return List.of();
    }
    if (requirementsObj instanceof List<?> list) {
      return list.stream()
          .filter(item -> item != null && !item.toString().isBlank())
          .map(Object::toString)
          .toList();
    }
    return List.of(requirementsObj.toString());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> parseEnv(Object envObj) {
    if (envObj == null) {
      return Map.of();
    }
    if (!(envObj instanceof Map<?, ?> raw)) {
      throw new IllegalArgumentException("env must be a mapping");
    }
    Map<String, String> env = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      env.put(
          entry.getKey().toString(), entry.getValue() != null ? entry.getValue().toString() : "");
    }
    return env;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractConfig(Map<String, Object> stageDefinition) {
    Object configObj = stageDefinition.get("config");
    if (configObj instanceof Map<?, ?> config) {
      return (Map<String, Object>) config;
    }
    return null;
  }

  private static String resolveStageType(Map<String, Object> stageDefinition) {
    Object typeObj = stageDefinition.get("type");
    return typeObj != null ? typeObj.toString().toLowerCase() : "echo";
  }

  private static String requireNonBlank(Map<String, Object> map, String key, String message) {
    String value = getString(map, key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String getString(Map<?, ?> map, String key) {
    Object v = map.get(key);
    return v != null ? v.toString() : null;
  }

  private static String shellQuote(String value) {
    if (value.contains("'")) {
      return "\"" + value.replace("\"", "\\\"") + "\"";
    }
    return "'" + value + "'";
  }
}
