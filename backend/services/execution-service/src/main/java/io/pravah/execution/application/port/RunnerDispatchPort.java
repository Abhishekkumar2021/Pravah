package io.pravah.execution.application.port;

import io.pravah.common.runner.RemoteJobSpecPayload;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dispatches jobs to the runner fleet via runner-service. */
public interface RunnerDispatchPort {

  /**
   * Attempts to assign the job to an online runner with a resolved execution spec.
   *
   * @return assigned runner id when dispatch succeeds
   */
  Optional<UUID> dispatch(
      UUID tenantId,
      UUID jobId,
      UUID executionId,
      UUID pipelineId,
      String jobName,
      RemoteJobSpecPayload spec,
      Map<String, String> labels);
}
