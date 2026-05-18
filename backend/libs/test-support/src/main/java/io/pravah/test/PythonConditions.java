package io.pravah.test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JUnit 5 conditions for Python stage integration tests (US-02.15). */
public final class PythonConditions {

  private static final Logger log = LoggerFactory.getLogger(PythonConditions.class);

  private PythonConditions() {}

  public static boolean isPythonAvailable() {
    String binary = System.getenv().getOrDefault("PRAVAH_PYTHON_BINARY", "python3");
    if ("false".equalsIgnoreCase(System.getenv("PRAVAH_PYTHON_ENABLED"))) {
      log.debug("Python disabled via PRAVAH_PYTHON_ENABLED=false");
      return false;
    }
    try {
      Process process = new ProcessBuilder(binary, "--version").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      boolean available = finished && process.exitValue() == 0;
      if (!available) {
        log.debug(
            "Python not available: binary={}, finished={}, exitCode={}",
            binary,
            finished,
            finished ? process.exitValue() : "N/A");
      }
      return available;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("Python availability check failed for binary={}", binary, e);
      return false;
    }
  }
}
