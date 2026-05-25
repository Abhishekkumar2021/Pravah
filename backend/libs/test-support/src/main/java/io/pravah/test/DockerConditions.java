package io.pravah.test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JUnit conditions for Docker-dependent tests. */
public final class DockerConditions {

  private static final Logger log = LoggerFactory.getLogger(DockerConditions.class);

  private DockerConditions() {}

  public static boolean isDockerAvailable() {
    if ("false".equalsIgnoreCase(System.getenv("PRAVAH_CONTAINER_ENABLED"))) {
      log.debug("Docker disabled via PRAVAH_CONTAINER_ENABLED=false");
      return false;
    }
    try {
      Process process =
          new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      boolean available = finished && process.exitValue() == 0;
      if (!available) {
        log.debug(
            "Docker not available: finished={}, exitCode={}",
            finished,
            finished ? process.exitValue() : "N/A");
      }
      return available;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("Docker availability check failed", e);
      return false;
    }
  }
}
