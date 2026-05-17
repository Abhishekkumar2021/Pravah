package io.pravah.execution.application;

import io.pravah.common.domain.StageTimeout;
import io.pravah.common.domain.StageTimeoutParser;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MVP executor: records stage id without running external processes.
 *
 * <p>Supports optional {@code simulate_exit_code} and {@code simulate_sleep_seconds} on stages for
 * local verification of retry and timeout (US-02.06, US-02.07).
 */
@Component
public class EchoEmbeddedStageExecutor implements EmbeddedStageExecutor {

  private final JobLogService jobLogService;
  private final ExecutionStageConfigResolver configResolver;

  public EchoEmbeddedStageExecutor(
      JobLogService jobLogService, ExecutionStageConfigResolver configResolver) {
    this.jobLogService = jobLogService;
    this.configResolver = configResolver;
  }

  @Override
  public StageExecutionResult execute(JobEntity job, ExecutionEntity execution) {
    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "[embedded-echo] Executing stage %s".formatted(job.getStageId()));

    Map<String, Object> definition = execution.getDefinitionSnapshot();
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "embedded-echo");
    output.put("stageId", job.getStageId());
    output.put("executionId", execution.getId().toString());

    Map<String, Object> rawConfig = extractStageConfig(definition, job.getStageId());
    if (rawConfig != null && !rawConfig.isEmpty()) {
      Map<String, Object> resolvedConfig = configResolver.resolveConfig(execution, rawConfig);
      Object message = resolvedConfig.get("message");
      if (message != null) {
        output.put("message", message);
        jobLogService.append(
            job.getId(), JobLogLevel.INFO, "[embedded-echo] %s".formatted(message));
      }
    }

    int sleepSeconds = resolveSimulateSleepSeconds(definition, job.getStageId());
    if (sleepSeconds > 0) {
      int timeoutExit = sleepWithTimeout(job, definition, job.getStageId(), sleepSeconds);
      if (timeoutExit != 0) {
        return new StageExecutionResult(timeoutExit, output);
      }
    }

    int exitCode = resolveSimulateExitCode(definition, job.getStageId());
    if (exitCode != 0) {
      jobLogService.append(
          job.getId(),
          JobLogLevel.WARN,
          "[embedded-echo] Simulating exit code %d (local test hook)".formatted(exitCode));
    }
    return new StageExecutionResult(exitCode, output);
  }

  private int sleepWithTimeout(
      JobEntity job, Map<String, Object> definition, String stageId, int sleepSeconds) {
    StageTimeout timeout = StageTimeoutParser.resolveForStage(definition, stageId);
    Instant deadline =
        timeout.isConfigured() && job.getStartedAt() != null
            ? job.getStartedAt().plusSeconds(timeout.timeoutSeconds())
            : null;

    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "[embedded-echo] Simulating sleep %ds (local test hook)".formatted(sleepSeconds));

    for (int i = 0; i < sleepSeconds; i++) {
      if (deadline != null && !Instant.now().isBefore(deadline)) {
        jobLogService.append(
            job.getId(),
            JobLogLevel.ERROR,
            "Stage timed out after %d seconds".formatted(timeout.timeoutSeconds()));
        return JobFailureService.EXIT_CODE_TIMEOUT;
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return JobFailureService.EXIT_CODE_TIMEOUT;
      }
    }
    return 0;
  }

  private static int resolveSimulateExitCode(Map<String, Object> definition, String stageId) {
    return resolveStageIntField(definition, stageId, "simulate_exit_code");
  }

  private static int resolveSimulateSleepSeconds(Map<String, Object> definition, String stageId) {
    return resolveStageIntField(definition, stageId, "simulate_sleep_seconds");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractStageConfig(
      Map<String, Object> definition, String stageId) {
    if (definition == null) {
      return null;
    }
    Object stages = definition.get("stages");
    if (!(stages instanceof List<?> list)) {
      return null;
    }
    for (Object o : list) {
      if (o instanceof Map<?, ?> stage) {
        Object id = stage.get("id");
        if (id != null && stageId.equals(id.toString())) {
          Object config = stage.get("config");
          return config instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        }
      }
    }
    return null;
  }

  private static int resolveStageIntField(
      Map<String, Object> definition, String stageId, String field) {
    if (definition == null) {
      return 0;
    }
    Object stages = definition.get("stages");
    if (!(stages instanceof List<?> list)) {
      return 0;
    }
    for (Object o : list) {
      if (o instanceof Map<?, ?> stage) {
        Object id = stage.get("id");
        if (id != null && stageId.equals(id.toString())) {
          Object value = stage.get(field);
          if (value instanceof Number n) {
            return Math.max(n.intValue(), 0);
          }
          return 0;
        }
      }
    }
    return 0;
  }
}
