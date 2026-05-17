package io.pravah.test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** JUnit conditions for Docker-dependent tests. */
public final class DockerConditions {

  private DockerConditions() {}

  public static boolean isDockerAvailable() {
    try {
      Process process =
          new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
