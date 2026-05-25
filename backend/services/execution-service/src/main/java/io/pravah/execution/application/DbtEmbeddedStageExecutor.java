package io.pravah.execution.application;

import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes dbt stages by invoking the {@code dbt} CLI in a project directory (US-02.x planned).
 *
 * <pre>
 * config:
 *   project_dir: /path/to/dbt/project
 *   select: "tag:nightly"   # optional
 *   target: dev             # optional dbt target
 *   profiles_dir: /path      # optional
 * </pre>
 */
@Component
public class DbtEmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(DbtEmbeddedStageExecutor.class);
  private static final int MAX_LOG_LINE = 16_384;

  private final JobLogService jobLogService;
  private final ExecutionStageConfigResolver configResolver;

  public DbtEmbeddedStageExecutor(
      JobLogService jobLogService, ExecutionStageConfigResolver configResolver) {
    this.jobLogService = jobLogService;
    this.configResolver = configResolver;
  }

  public EmbeddedStageExecutor.StageExecutionResult execute(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "dbt");
    output.put("stageId", job.getStageId());

    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      output.put("error", "dbt stage missing required 'config' section");
      return fail(job, output);
    }

    Map<String, Object> config;
    try {
      config = configResolver.resolveConfig(execution, rawConfig);
    } catch (RuntimeException e) {
      output.put("error", "Failed to resolve dbt configuration");
      return fail(job, output);
    }

    String projectDir = stringVal(config, "project_dir");
    if (projectDir == null || projectDir.isBlank()) {
      output.put("error", "dbt stage requires 'project_dir' in config");
      return fail(job, output);
    }

    Path projectPath = Path.of(projectDir);
    if (!Files.isDirectory(projectPath)) {
      output.put("error", "dbt project_dir does not exist: " + projectDir);
      return fail(job, output);
    }

    List<String> command = buildDbtCommand(config);
    jobLogService.append(
        job.getId(), JobLogLevel.INFO, "[dbt] Running: " + String.join(" ", command));

    Instant start = Instant.now();
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(projectPath.toFile());
      pb.redirectErrorStream(true);

      String profilesDir = stringVal(config, "profiles_dir");
      if (profilesDir != null && !profilesDir.isBlank()) {
        pb.environment().put("DBT_PROFILES_DIR", profilesDir);
      }

      Process process = pb.start();
      List<String> logLines = new ArrayList<>();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.length() > MAX_LOG_LINE) {
            line = line.substring(0, MAX_LOG_LINE) + "...";
          }
          logLines.add(line);
          jobLogService.append(job.getId(), JobLogLevel.INFO, "[dbt] " + line);
        }
      }

      long timeoutSeconds = longVal(config, "timeout_seconds", 3600L);
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        output.put("error", "dbt run timed out after " + timeoutSeconds + "s");
        return new EmbeddedStageExecutor.StageExecutionResult(1, output);
      }

      int exitCode = process.exitValue();
      output.put("exit_code", exitCode);
      output.put("duration_ms", Duration.between(start, Instant.now()).toMillis());
      output.put("command", command);
      output.put("log_line_count", logLines.size());

      if (exitCode != 0) {
        output.put("error", "dbt exited with code " + exitCode);
        jobLogService.append(job.getId(), JobLogLevel.ERROR, "[dbt] Failed with exit " + exitCode);
      }

      return new EmbeddedStageExecutor.StageExecutionResult(exitCode, output);

    } catch (Exception e) {
      log.error("dbt execution failed for job {}", job.getId(), e);
      output.put("error", "dbt execution failed: " + e.getMessage());
      return fail(job, output);
    }
  }

  private List<String> buildDbtCommand(Map<String, Object> config) {
    List<String> cmd = new ArrayList<>();
    cmd.add(stringVal(config, "dbt_binary", "dbt"));
    cmd.add("run");

    String target = stringVal(config, "target");
    if (target != null && !target.isBlank()) {
      cmd.add("--target");
      cmd.add(target);
    }

    String select = stringVal(config, "select");
    if (select != null && !select.isBlank()) {
      cmd.add("--select");
      cmd.add(select);
    }

    return cmd;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractConfig(Map<String, Object> stageDefinition) {
    Object config = stageDefinition.get("config");
    return config instanceof Map ? (Map<String, Object>) config : null;
  }

  private static String stringVal(Map<String, Object> config, String key) {
    return stringVal(config, key, null);
  }

  private static String stringVal(Map<String, Object> config, String key, String defaultValue) {
    Object v = config.get(key);
    return v != null ? v.toString() : defaultValue;
  }

  private static long longVal(Map<String, Object> config, String key, long defaultValue) {
    Object v = config.get(key);
    if (v instanceof Number n) {
      return n.longValue();
    }
    return defaultValue;
  }

  private EmbeddedStageExecutor.StageExecutionResult fail(
      JobEntity job, Map<String, Object> output) {
    jobLogService.append(
        job.getId(), JobLogLevel.ERROR, "[dbt] " + output.getOrDefault("error", "failed"));
    return new EmbeddedStageExecutor.StageExecutionResult(1, output);
  }
}
