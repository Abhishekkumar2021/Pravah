package io.pravah.common.runner;

import java.util.List;
import java.util.Map;

/**
 * Resolved stage execution spec sent from execution-service to runner-service for remote runs.
 *
 * <p>Maps to {@code JobSpec} in {@code runner_service.proto} after assignment.
 */
public record RemoteJobSpecPayload(
    String executor,
    String image,
    List<String> commands,
    Map<String, String> environment,
    long timeoutSeconds,
    Long memoryBytes,
    Double cpuCores) {

  public static final long DEFAULT_TIMEOUT_SECONDS = 3600L;

  public RemoteJobSpecPayload {
    if (executor == null || executor.isBlank()) {
      throw new IllegalArgumentException("executor is required");
    }
    if (commands == null) {
      commands = List.of();
    }
    if (environment == null) {
      environment = Map.of();
    }
    if (timeoutSeconds <= 0) {
      timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }
  }
}
