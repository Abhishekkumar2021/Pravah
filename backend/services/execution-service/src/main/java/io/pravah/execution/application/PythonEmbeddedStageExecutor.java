package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.PythonStageOutputParser;
import io.pravah.common.domain.StageTimeout;
import io.pravah.common.domain.StageTimeoutParser;
import io.pravah.execution.application.port.PythonLogLineConsumer;
import io.pravah.execution.application.port.PythonRunRequest;
import io.pravah.execution.application.port.PythonRunResult;
import io.pravah.execution.application.port.PythonRuntime;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.artifact.ArtifactMetadata;
import io.pravah.execution.infrastructure.artifact.ArtifactType;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes Python stages using a per-job virtualenv on the execution-service host (US-02.15).
 *
 * <p>Stage config:
 *
 * <pre>
 * stages:
 *   - id: transform
 *     type: python
 *     config:
 *       script: |
 *         import json
 *         print(json.dumps({"rows": 1}))
 *       requirements:
 *         - pandas==2.0.0
 *       python_version: "3.12"   # publish-time validation only
 *       env:
 *         MY_FLAG: "true"
 * </pre>
 *
 * <p>Alternatively use {@code command: ["python", "-c", "..."]}. Structured output is parsed from
 * the last JSON line or a {@link PythonStageOutputParser#OUTPUT_LINE_PREFIX} line on stdout.
 */
@Component
public class PythonEmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(PythonEmbeddedStageExecutor.class);
  private static final int MAX_LOG_LINE_LENGTH = 16_384;
  private static final String CONTEXT_FILE = "context.json";
  private static final String SCRIPT_FILE = "main.py";
  private static final String OUTPUT_DIR = "output";

  private final JobLogService jobLogService;
  private final PythonRuntime pythonRuntime;
  private final ExecutionStageConfigResolver configResolver;
  private final ObjectMapper objectMapper;
  private final ArtifactPublisher artifactPublisher;

  public PythonEmbeddedStageExecutor(
      JobLogService jobLogService,
      PythonRuntime pythonRuntime,
      ExecutionStageConfigResolver configResolver,
      ObjectMapper objectMapper,
      ArtifactPublisher artifactPublisher) {
    this.jobLogService = jobLogService;
    this.pythonRuntime = pythonRuntime;
    this.configResolver = configResolver;
    this.objectMapper = objectMapper;
    this.artifactPublisher = artifactPublisher;
  }

  public EmbeddedStageExecutor.StageExecutionResult execute(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "python");
    output.put("stageId", job.getStageId());

    if (!pythonRuntime.isAvailable()) {
      String error =
          "Python runtime is not available. Ensure python3 is installed and pravah.python.enabled=true";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[python] " + error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      String error = "Python stage missing required 'config' section";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[python] " + error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Map<String, Object> resolvedConfig;
    try {
      resolvedConfig = configResolver.resolveConfig(execution, rawConfig);
    } catch (RuntimeException e) {
      log.error(
          "Python config resolution failed",
          kv("job_id", job.getId()),
          kv("stage_id", job.getStageId()),
          e);
      String userError = "Failed to resolve Python stage configuration. Check logs for details.";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[python] " + userError);
      output.put("error", userError);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Path workspace = null;
    try {
      workspace = Files.createTempDirectory("pravah-py-" + job.getId());
      Path outputDir = Files.createDirectory(workspace.resolve(OUTPUT_DIR));
      writeContextFile(workspace, job, execution, resolvedConfig);

      List<String> requirements = parseRequirements(resolvedConfig);
      String basePython = resolveBasePythonBinary(resolvedConfig);
      Path venvPython = pythonRuntime.prepareWorkspace(workspace, requirements, basePython);

      List<String> command = buildCommand(resolvedConfig, venvPython, workspace);
      Map<String, String> environment = parseEnv(resolvedConfig);
      environment = new LinkedHashMap<>(environment);
      environment.put("PRAVAH_CONTEXT_PATH", workspace.resolve(CONTEXT_FILE).toString());
      environment.put("PRAVAH_OUTPUT_DIR", outputDir.toString());
      environment.put("PRAVAH_STAGE_ID", job.getStageId());
      environment.put("PRAVAH_EXECUTION_ID", execution.getId().toString());

      StageTimeout timeout =
          StageTimeoutParser.resolveForStage(execution.getDefinitionSnapshot(), job.getStageId());
      Duration runTimeout =
          timeout.isConfigured() ? Duration.ofSeconds(timeout.timeoutSeconds()) : Duration.ZERO;

      jobLogService.append(
          job.getId(),
          JobLogLevel.INFO,
          "[python] Running stage %s in workspace %s".formatted(job.getStageId(), workspace));

      Instant start = Instant.now();
      PythonRunResult result =
          pythonRuntime.run(
              new PythonRunRequest(workspace, venvPython, command, environment, runTimeout),
              createLogConsumer(job.getId()));

      Duration duration = Duration.between(start, Instant.now());
      output.put("duration_ms", duration.toMillis());
      output.put("exit_code", result.exitCode());
      output.put("timed_out", result.timedOut());

      Optional<Map<String, Object>> structured =
          PythonStageOutputParser.parseStdout(result.stdout());
      structured.ifPresent(parsed -> output.put("result", parsed));

      List<ArtifactMetadata> artifacts = publishOutputArtifacts(job, execution, outputDir);
      if (!artifacts.isEmpty()) {
        output.put("artifacts", artifactPublisher.toOutputReference(artifacts));
      }

      if (result.timedOut()) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.ERROR,
            "[python] Process timed out after %d seconds"
                .formatted(timeout.isConfigured() ? timeout.timeoutSeconds() : 0));
        return new EmbeddedStageExecutor.StageExecutionResult(
            JobFailureService.EXIT_CODE_TIMEOUT, output);
      }

      if (result.exitCode() != 0) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.ERROR,
            "[python] Process exited with code %d".formatted(result.exitCode()));
        return new EmbeddedStageExecutor.StageExecutionResult(result.exitCode(), output);
      }

      jobLogService.append(
          job.getId(),
          JobLogLevel.INFO,
          "[python] Completed successfully in %d ms".formatted(duration.toMillis()));
      return new EmbeddedStageExecutor.StageExecutionResult(0, output);

    } catch (IOException | RuntimeException e) {
      log.error(
          "Python stage error", kv("job_id", job.getId()), kv("stage_id", job.getStageId()), e);
      String userError = "Python execution failed. Check logs for details.";
      output.put("error", userError);
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[python] " + userError);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    } finally {
      if (workspace != null) {
        deleteRecursively(workspace);
      }
    }
  }

  private void writeContextFile(
      Path workspace, JobEntity job, ExecutionEntity execution, Map<String, Object> resolvedConfig)
      throws IOException {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("execution_id", execution.getId().toString());
    context.put("pipeline_id", execution.getPipelineId().toString());
    context.put("pipeline_version", execution.getPipelineVersion());
    context.put("job_id", job.getId().toString());
    context.put("stage_id", job.getStageId());
    context.put("attempt", job.getAttempt());
    context.put("config", resolvedConfig);
    context.put("parameters", execution.getParameters());
    byte[] json = objectMapper.writeValueAsBytes(context);
    Files.write(workspace.resolve(CONTEXT_FILE), json);
  }

  private static List<String> buildCommand(
      Map<String, Object> config, Path venvPython, Path workspace) throws IOException {
    Object commandObj = config.get("command");
    if (commandObj != null) {
      List<String> command = parseCommand(commandObj);
      return normalizePythonCommand(command, venvPython);
    }

    Object scriptObj = config.get("script");
    if (scriptObj == null || scriptObj.toString().isBlank()) {
      throw new IllegalArgumentException("Python stage requires config.script or config.command");
    }
    Path scriptPath = workspace.resolve(SCRIPT_FILE);
    Files.writeString(scriptPath, scriptObj.toString(), StandardCharsets.UTF_8);
    return List.of(venvPython.toString(), scriptPath.toString());
  }

  private static List<String> normalizePythonCommand(List<String> command, Path venvPython) {
    if (command.isEmpty()) {
      throw new IllegalArgumentException("command must not be empty");
    }
    String first = command.get(0).toLowerCase();
    if (first.equals("python") || first.equals("python3") || first.startsWith("python3.")) {
      List<String> normalized = new ArrayList<>(command);
      normalized.set(0, venvPython.toString());
      return normalized;
    }
    return command;
  }

  private static List<String> parseRequirements(Map<String, Object> config) {
    Object requirements = config.get("requirements");
    if (requirements instanceof List<?> list) {
      List<String> specs = new ArrayList<>();
      for (Object item : list) {
        if (item != null && !item.toString().isBlank()) {
          specs.add(item.toString().trim());
        }
      }
      return specs;
    }
    return List.of();
  }

  private static String resolveBasePythonBinary(Map<String, Object> config) {
    Object version = config.get("python_version");
    if (version == null || version.toString().isBlank()) {
      return null;
    }
    String raw = version.toString().trim();
    if (raw.startsWith("python")) {
      return raw;
    }
    return "python" + raw;
  }

  private PythonLogLineConsumer createLogConsumer(java.util.UUID jobId) {
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
  private static Map<String, String> parseEnv(Map<String, Object> config) {
    Object envObj = config.get("env");
    if (envObj == null) {
      return new LinkedHashMap<>();
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
    if (stageDefinition == null) {
      return null;
    }
    Object configObj = stageDefinition.get("config");
    if (configObj instanceof Map<?, ?> config) {
      return (Map<String, Object>) config;
    }
    return null;
  }

  private List<ArtifactMetadata> publishOutputArtifacts(
      JobEntity job, ExecutionEntity execution, Path outputDir) {
    List<ArtifactMetadata> published = new ArrayList<>();

    try {
      if (!Files.exists(outputDir)) {
        return published;
      }

      try (var stream = Files.list(outputDir)) {
        stream
            .filter(Files::isRegularFile)
            .forEach(
                file -> {
                  ArtifactMetadata metadata =
                      artifactPublisher.publishFile(job, execution, ArtifactType.OUTPUT, file);
                  if (metadata != null) {
                    published.add(metadata);
                  }
                });
      }
    } catch (IOException e) {
      log.warn(
          "Failed to list output directory for artifact upload",
          kv("job_id", job.getId()),
          kv("output_dir", outputDir),
          e);
    }

    return published;
  }

  private static void deleteRecursively(Path root) {
    try {
      if (Files.isDirectory(root)) {
        try (var stream = Files.list(root)) {
          stream.forEach(PythonEmbeddedStageExecutor::deleteRecursively);
        }
      }
      Files.deleteIfExists(root);
    } catch (IOException e) {
      log.debug("Failed to delete python workspace {}", root, e);
    }
  }
}
