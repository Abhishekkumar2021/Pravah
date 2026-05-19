package io.pravah.execution.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dispatches jobs to the runner fleet via runner-service. */
public interface RunnerDispatchPort {

  /**
   * Attempts to assign the job to an online runner.
   *
   * @return assigned runner id when dispatch succeeds
   */
  Optional<UUID> dispatch(
      UUID tenantId, UUID jobId, UUID executionId, String stageType, Map<String, String> labels);
}
