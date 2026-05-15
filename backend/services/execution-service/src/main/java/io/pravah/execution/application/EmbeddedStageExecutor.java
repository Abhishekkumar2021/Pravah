package io.pravah.execution.application;

import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.Map;

/**
 * Executes a queued job inside the embedded worker (placeholder for future runner / container
 * execution).
 */
public interface EmbeddedStageExecutor {

  /**
   * Runs the job and returns exit status and structured output.
   *
   * @param job the job in {@code RUNNING} state (assigned to the embedded runner before invoke)
   * @param execution parent execution
   */
  StageExecutionResult execute(JobEntity job, ExecutionEntity execution);

  record StageExecutionResult(int exitCode, Map<String, Object> output) {}
}
