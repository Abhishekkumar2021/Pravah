package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.ContainerResourceParser;
import io.pravah.common.domain.ResourceProfiles;
import io.pravah.common.domain.StageTimeout;
import io.pravah.common.domain.StageTimeoutParser;
import io.pravah.execution.application.port.ContainerLogLineConsumer;
import io.pravah.execution.application.port.ContainerRunRequest;
import io.pravah.execution.application.port.ContainerRunResult;
import io.pravah.execution.application.port.ContainerRuntime;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes container stages using Docker on the execution-service host (US-02.17).
 *
 * <p>Stage config:
 *
 * <pre>
 * stages:
 *   - id: run_tool
 *     type: container
 *     config:
 *       image: alpine:3.19
 *       command: ["echo", "hello"]
 *       env:
 *         API_TOKEN: "${secret.api_token}"
 *       resources:
 *         memory: 512Mi
 *         cpus: "0.5"
 * </pre>
 */
@Component
public class ContainerEmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(ContainerEmbeddedStageExecutor.class);
  private static final int MAX_LOG_LINE_LENGTH = 16_384;

  private final JobLogService jobLogService;
  private final ContainerRuntime containerRuntime;
  private final ExecutionStageConfigResolver configResolver;

  public ContainerEmbeddedStageExecutor(
      JobLogService jobLogService,
      ContainerRuntime containerRuntime,
      ExecutionStageConfigResolver configResolver) {
    this.jobLogService = jobLogService;
    this.containerRuntime = containerRuntime;
    this.configResolver = configResolver;
  }

  public EmbeddedStageExecutor.StageExecutionResult execute(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "container");
    output.put("stageId", job.getStageId());

    if (!containerRuntime.isAvailable()) {
      String error =
          "Container runtime is not available. Ensure Docker is installed and pravah.container.enabled=true";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[container] " + error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      String error = "Container stage missing required 'config' section";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[container] " + error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Map<String, Object> resolvedConfig;
    try {
      resolvedConfig = configResolver.resolveConfig(execution, rawConfig);
    } catch (RuntimeException e) {
      log.error(
          "Config resolution failed",
          kv("job_id", job.getId()),
          kv("stage_id", job.getStageId()),
          e);
      String userError = "Failed to resolve container configuration. Check logs for details.";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[container] " + userError);
      output.put("error", userError);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    String image = getString(resolvedConfig, "image");
    if (image == null || image.isBlank()) {
      String error = "Container stage missing required 'image' in config";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[container] " + error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    List<String> command = parseCommand(resolvedConfig.get("command"));
    Map<String, String> environment = parseEnv(resolvedConfig.get("env"));
    ResourceProfiles.ResourceLimits limits = parseResourceLimits(resolvedConfig);
    String memoryLimit =
        limits.memory() != null
            ? ContainerResourceParser.toDockerMemoryLimit(limits.memory())
            : null;
    String cpuLimit =
        limits.cpus() != null ? ContainerResourceParser.toDockerCpuLimit(limits.cpus()) : null;

    String containerName = "pravah-job-" + job.getId();
    StageTimeout timeout =
        StageTimeoutParser.resolveForStage(execution.getDefinitionSnapshot(), job.getStageId());
    Duration runTimeout =
        timeout.isConfigured() ? Duration.ofSeconds(timeout.timeoutSeconds()) : Duration.ZERO;

    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "[container] Running image %s for stage %s".formatted(image.trim(), job.getStageId()));

    output.put("image", image.trim());
    output.put("container_name", containerName);

    ContainerRunRequest runRequest =
        new ContainerRunRequest(
            containerName, image.trim(), command, environment, memoryLimit, cpuLimit, runTimeout);

    Instant start = Instant.now();
    ContainerLogLineConsumer logConsumer = createLogConsumer(job.getId());

    try {
      ContainerRunResult result = containerRuntime.run(runRequest, logConsumer);
      Duration duration = Duration.between(start, Instant.now());
      output.put("duration_ms", duration.toMillis());
      output.put("exit_code", result.exitCode());
      output.put("timed_out", result.timedOut());

      if (result.timedOut()) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.ERROR,
            "[container] Container timed out after %d seconds"
                .formatted(timeout.isConfigured() ? timeout.timeoutSeconds() : 0));
        return new EmbeddedStageExecutor.StageExecutionResult(result.exitCode(), output);
      }

      if (result.exitCode() != 0) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.ERROR,
            "[container] Container exited with code %d".formatted(result.exitCode()));
        log.warn(
            "Container stage failed",
            kv("job_id", job.getId()),
            kv("stage_id", job.getStageId()),
            kv("exit_code", result.exitCode()));
        return new EmbeddedStageExecutor.StageExecutionResult(result.exitCode(), output);
      }

      jobLogService.append(
          job.getId(),
          JobLogLevel.INFO,
          "[container] Completed successfully in %d ms".formatted(duration.toMillis()));
      return new EmbeddedStageExecutor.StageExecutionResult(0, output);

    } catch (RuntimeException e) {
      Duration duration = Duration.between(start, Instant.now());
      output.put("duration_ms", duration.toMillis());
      log.error(
          "Container stage error", kv("job_id", job.getId()), kv("stage_id", job.getStageId()), e);
      String userError = "Container execution failed. Check logs for details.";
      output.put("error", userError);
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[container] " + userError);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }
  }

  private ContainerLogLineConsumer createLogConsumer(java.util.UUID jobId) {
    return (line, stderr) -> {
      if (line == null || line.isBlank()) {
        return;
      }
      String message =
          line.length() > MAX_LOG_LINE_LENGTH ? line.substring(0, MAX_LOG_LINE_LENGTH) : line;
      JobLogLevel level = stderr ? JobLogLevel.WARN : JobLogLevel.INFO;
      jobLogService.append(jobId, level, (stderr ? "[stderr] " : "") + message);
    };
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
      String key = entry.getKey().toString();
      Object value = entry.getValue();
      env.put(key, value != null ? value.toString() : "");
    }
    return env;
  }

  private static ResourceProfiles.ResourceLimits parseResourceLimits(Map<String, Object> config) {
    Object resources = config.get("resources");
    if (!(resources instanceof Map<?, ?> map)) {
      return new ResourceProfiles.ResourceLimits(null, null);
    }
    return ResourceProfiles.mergeWithProfile(map);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractConfig(Map<String, Object> stageDefinition) {
    if (stageDefinition == null) {
      return null;
    }
    Object configObj = stageDefinition.get("config");
    if (configObj instanceof Map<?, ?> config) {
      return (Map<String, Object>) config;
    }
    return null;
  }

  private static String getString(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v != null ? v.toString() : null;
  }
}
