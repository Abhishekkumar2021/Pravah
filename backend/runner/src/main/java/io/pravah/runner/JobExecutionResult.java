package io.pravah.runner;

import java.util.Map;

/** Result of a local job execution on the runner agent. */
public record JobExecutionResult(int exitCode, Map<String, Object> output) {

  public JobExecutionResult {
    if (output == null) {
      output = Map.of();
    }
  }

  public static JobExecutionResult success(Map<String, Object> output) {
    return new JobExecutionResult(0, output);
  }

  public static JobExecutionResult failure(int exitCode) {
    return new JobExecutionResult(exitCode, Map.of());
  }
}
