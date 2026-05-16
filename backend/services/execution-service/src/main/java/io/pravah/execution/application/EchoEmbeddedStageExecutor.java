package io.pravah.execution.application;

import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MVP executor: records stage id without running external processes.
 *
 * <p>Always returns exitCode=0 so the happy-path DAG scheduling can be exercised. For failure-path
 * testing, provide a test-only implementation that can simulate non-zero exits based on stage
 * configuration or test fixtures.
 *
 * <p>Future implementations will read stage configuration from the pipeline definition and execute
 * actual work (shell commands, container invocations, remote worker dispatch).
 */
@Component
public class EchoEmbeddedStageExecutor implements EmbeddedStageExecutor {

  private final JobLogService jobLogService;

  public EchoEmbeddedStageExecutor(JobLogService jobLogService) {
    this.jobLogService = jobLogService;
  }

  @Override
  public StageExecutionResult execute(JobEntity job, ExecutionEntity execution) {
    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "[embedded-echo] Executing stage %s".formatted(job.getStageId()));

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "embedded-echo");
    output.put("stageId", job.getStageId());
    output.put("executionId", execution.getId().toString());

    int exitCode = resolveSimulateExitCode(execution.getDefinitionSnapshot(), job.getStageId());
    if (exitCode != 0) {
      jobLogService.append(
          job.getId(),
          JobLogLevel.WARN,
          "[embedded-echo] Simulating exit code %d (local test hook)".formatted(exitCode));
    }
    return new StageExecutionResult(exitCode, output);
  }

  /**
   * Optional stage field {@code simulate_exit_code} in the pipeline definition (local / MVP testing
   * only). Enables API verification of auto-retry without real runners.
   */
  private static int resolveSimulateExitCode(Map<String, Object> definition, String stageId) {
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
          Object code = stage.get("simulate_exit_code");
          if (code instanceof Number n) {
            return n.intValue();
          }
          return 0;
        }
      }
    }
    return 0;
  }
}
