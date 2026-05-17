package io.pravah.execution.infrastructure.container;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.application.JobFailureService;
import io.pravah.execution.application.port.ContainerLogLineConsumer;
import io.pravah.execution.application.port.ContainerRunRequest;
import io.pravah.execution.application.port.ContainerRunResult;
import io.pravah.execution.application.port.ContainerRuntime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs containers using the Docker CLI ({@code docker run}).
 *
 * <p>Requires Docker Engine on the host. Used for alpha embedded execution; production K8s runners
 * will use a different implementation.
 */
@Component
public class DockerCliContainerRuntime implements ContainerRuntime {

  private static final Logger log = LoggerFactory.getLogger(DockerCliContainerRuntime.class);

  private final String dockerBinary;
  private final boolean enabled;

  public DockerCliContainerRuntime(
      @Value("${pravah.container.enabled:true}") boolean enabled,
      @Value("${pravah.container.docker-binary:docker}") String dockerBinary) {
    this.enabled = enabled;
    this.dockerBinary = dockerBinary != null && !dockerBinary.isBlank() ? dockerBinary : "docker";
  }

  @Override
  public boolean isAvailable() {
    if (!enabled) {
      return false;
    }
    try {
      Process process =
          new ProcessBuilder(dockerBinary, "version", "--format", "{{.Server.Version}}").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public ContainerRunResult run(ContainerRunRequest request, ContainerLogLineConsumer logConsumer) {
    List<String> command = buildDockerCommand(request);
    log.debug(
        "Starting container",
        kv("container_name", request.containerName()),
        kv("image", request.image()),
        kv("memory_limit", request.memoryLimit()),
        kv("cpu_limit", request.cpuLimit()));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(false);

    try {
      Process process = processBuilder.start();
      Thread stdoutReader =
          new Thread(
              () -> streamLogs(process.getInputStream(), false, logConsumer),
              "container-stdout-" + request.containerName());
      Thread stderrReader =
          new Thread(
              () -> streamLogs(process.getErrorStream(), true, logConsumer),
              "container-stderr-" + request.containerName());
      stdoutReader.start();
      stderrReader.start();

      boolean timedOut = false;
      if (request.timeout().isZero()) {
        process.waitFor();
      } else {
        boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
          timedOut = true;
          killContainer(request.containerName());
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }

      stdoutReader.join(5_000);
      stderrReader.join(5_000);

      int exitCode = timedOut ? JobFailureService.EXIT_CODE_TIMEOUT : process.exitValue();
      return new ContainerRunResult(exitCode, timedOut);

    } catch (IOException e) {
      log.error(
          "Failed to start container process",
          kv("container_name", request.containerName()),
          kv("image", request.image()),
          e);
      throw new IllegalStateException("Failed to start container process", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      killContainer(request.containerName());
      return new ContainerRunResult(JobFailureService.EXIT_CODE_TIMEOUT, true);
    }
  }

  private List<String> buildDockerCommand(ContainerRunRequest request) {
    List<String> cmd = new ArrayList<>();
    cmd.add(dockerBinary);
    cmd.add("run");
    cmd.add("--rm");
    cmd.add("--init");
    cmd.add("--network");
    cmd.add("none");
    cmd.add("--user");
    cmd.add("65534:65534");
    cmd.add("--name");
    cmd.add(request.containerName());
    if (request.memoryLimit() != null && !request.memoryLimit().isBlank()) {
      cmd.add("--memory");
      cmd.add(request.memoryLimit());
    }
    if (request.cpuLimit() != null && !request.cpuLimit().isBlank()) {
      cmd.add("--cpus");
      cmd.add(request.cpuLimit());
    }
    for (var entry : request.environment().entrySet()) {
      cmd.add("-e");
      cmd.add(entry.getKey() + "=" + entry.getValue());
    }
    cmd.add(request.image());
    cmd.addAll(request.command());
    return List.copyOf(cmd);
  }

  private void killContainer(String containerName) {
    try {
      Process kill = new ProcessBuilder(dockerBinary, "kill", containerName).start();
      if (!kill.waitFor(10, TimeUnit.SECONDS)) {
        log.error("docker kill timed out", kv("container_name", containerName));
      }
      Process inspect = new ProcessBuilder(dockerBinary, "inspect", containerName).start();
      if (inspect.waitFor(5, TimeUnit.SECONDS) && inspect.exitValue() == 0) {
        log.warn("Container still exists after kill", kv("container_name", containerName));
      }
    } catch (IOException | InterruptedException e) {
      log.warn("Failed to kill container", kv("container_name", containerName), e);
      Thread.currentThread().interrupt();
    }
  }

  private static void streamLogs(
      InputStream stream, boolean stderr, ContainerLogLineConsumer consumer) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        consumer.onLine(line, stderr);
      }
    } catch (IOException e) {
      log.debug("Container log stream closed", e);
    }
  }
}
