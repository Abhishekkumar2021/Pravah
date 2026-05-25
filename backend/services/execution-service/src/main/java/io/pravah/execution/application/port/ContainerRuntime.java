package io.pravah.execution.application.port;

/**
 * Abstraction for running OCI containers during embedded stage execution (US-02.17).
 *
 * <p>Production deployments may use Docker CLI locally or delegate to a remote runner in future
 * releases.
 */
public interface ContainerRuntime {

  /** Returns true when the runtime can execute containers on this host. */
  boolean isAvailable();

  /**
   * Runs a container and blocks until it exits or times out.
   *
   * @param request container specification
   * @param logConsumer receives stdout/stderr lines (must not log secret values)
   * @return exit code and timeout flag
   */
  ContainerRunResult run(ContainerRunRequest request, ContainerLogLineConsumer logConsumer);
}
