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
 * Executes Spark stages via {@code spark-submit} (local or cluster mode).
 *
 * <pre>
 * config:
 *   app_jar: /path/to/app.jar
 *   main_class: com.example.App
 *   args: ["--input", "s3://bucket/data"]
 *   master: local[*]    # or yarn, k8s://...
 *   deploy_mode: client
 * </pre>
 */
@Component
public class SparkEmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(SparkEmbeddedStageExecutor.class);
  private static final int MAX_LOG_LINE = 16_384;

  private final JobLogService jobLogService;
  private final ExecutionStageConfigResolver configResolver;

  public SparkEmbeddedStageExecutor(
      JobLogService jobLogService, ExecutionStageConfigResolver configResolver) {
    this.jobLogService = jobLogService;
    this.configResolver = configResolver;
  }

  public EmbeddedStageExecutor.StageExecutionResult execute(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "spark");
    output.put("stageId", job.getStageId());

    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      output.put("error", "spark stage missing required 'config' section");
      return fail(job, output);
    }

    Map<String, Object> config;
    try {
      config = configResolver.resolveConfig(execution, rawConfig);
    } catch (RuntimeException e) {
      output.put("error", "Failed to resolve spark configuration");
      return fail(job, output);
    }

    String appJar = stringVal(config, "app_jar");
    String mainClass = stringVal(config, "main_class");
    if (appJar == null || appJar.isBlank()) {
      output.put("error", "spark stage requires 'app_jar' in config");
      return fail(job, output);
    }
    if (mainClass == null || mainClass.isBlank()) {
      output.put("error", "spark stage requires 'main_class' in config");
      return fail(job, output);
    }

    if (!Files.exists(Path.of(appJar))) {
      output.put("error", "app_jar not found: " + appJar);
      return fail(job, output);
    }

    String sparkSubmit = resolveSparkSubmit(config);
    List<String> command = buildSparkSubmitCommand(sparkSubmit, config, appJar, mainClass);

    jobLogService.append(
        job.getId(), JobLogLevel.INFO, "[spark] Running: " + String.join(" ", command));

    Instant start = Instant.now();
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);

      String sparkHome = System.getenv("SPARK_HOME");
      if (sparkHome != null) {
        pb.environment().putIfAbsent("SPARK_HOME", sparkHome);
      }

      Process process = pb.start();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.length() > MAX_LOG_LINE) {
            line = line.substring(0, MAX_LOG_LINE) + "...";
          }
          jobLogService.append(job.getId(), JobLogLevel.INFO, "[spark] " + line);
        }
      }

      long timeoutSeconds = longVal(config, "timeout_seconds", 7200L);
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        output.put("error", "spark-submit timed out after " + timeoutSeconds + "s");
        return new EmbeddedStageExecutor.StageExecutionResult(1, output);
      }

      int exitCode = process.exitValue();
      output.put("exit_code", exitCode);
      output.put("duration_ms", Duration.between(start, Instant.now()).toMillis());
      output.put("command", command);

      if (exitCode != 0) {
        output.put("error", "spark-submit exited with code " + exitCode);
        jobLogService.append(
            job.getId(), JobLogLevel.ERROR, "[spark] Failed with exit " + exitCode);
      }

      return new EmbeddedStageExecutor.StageExecutionResult(exitCode, output);

    } catch (Exception e) {
      log.error("Spark execution failed for job {}", job.getId(), e);
      output.put("error", "spark execution failed: " + e.getMessage());
      return fail(job, output);
    }
  }

  private String resolveSparkSubmit(Map<String, Object> config) {
    String explicit = stringVal(config, "spark_submit");
    if (explicit != null && !explicit.isBlank()) {
      return explicit;
    }
    String sparkHome = System.getenv("SPARK_HOME");
    if (sparkHome != null) {
      return sparkHome + "/bin/spark-submit";
    }
    return "spark-submit";
  }

  private List<String> buildSparkSubmitCommand(
      String sparkSubmit, Map<String, Object> config, String appJar, String mainClass) {

    List<String> cmd = new ArrayList<>();
    cmd.add(sparkSubmit);
    cmd.add("--master");
    cmd.add(stringVal(config, "master", "local[*]"));
    cmd.add("--deploy-mode");
    cmd.add(stringVal(config, "deploy_mode", "client"));
    cmd.add("--class");
    cmd.add(mainClass);
    cmd.add(appJar);

    Object argsObj = config.get("args");
    if (argsObj instanceof List<?> args) {
      for (Object arg : args) {
        if (arg != null) {
          cmd.add(arg.toString());
        }
      }
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
        job.getId(), JobLogLevel.ERROR, "[spark] " + output.getOrDefault("error", "failed"));
    return new EmbeddedStageExecutor.StageExecutionResult(1, output);
  }
}
