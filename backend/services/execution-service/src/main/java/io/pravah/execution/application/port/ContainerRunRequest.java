package io.pravah.execution.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Request to run a container via {@link ContainerRuntime}. */
public record ContainerRunRequest(
    String containerName,
    String image,
    List<String> command,
    Map<String, String> environment,
    String memoryLimit,
    String cpuLimit,
    Duration timeout) {

  public ContainerRunRequest {
    if (containerName == null || !containerName.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*$")) {
      throw new IllegalArgumentException("Invalid container name: " + containerName);
    }
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("Image is required");
    }
    command = command != null ? List.copyOf(command) : List.of();
    environment = environment != null ? Map.copyOf(environment) : Map.of();
    timeout = timeout != null ? timeout : Duration.ZERO;
  }
}
