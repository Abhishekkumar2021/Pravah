package io.pravah.execution.infrastructure.python;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.application.port.PythonLogLineConsumer;
import io.pravah.execution.application.port.PythonRunRequest;
import io.pravah.execution.application.port.PythonRunResult;
import io.pravah.execution.application.port.PythonRuntime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs Python stages using the host {@code python3} binary and per-job virtualenv (US-02.15). */
@Component
public class LocalProcessPythonRuntime implements PythonRuntime {

  private static final Logger log = LoggerFactory.getLogger(LocalProcessPythonRuntime.class);

  private final boolean enabled;
  private final String pythonBinary;

  public LocalProcessPythonRuntime(
      @Value("${pravah.python.enabled:true}") boolean enabled,
      @Value("${pravah.python.binary:python3}") String pythonBinary) {
    this.enabled = enabled;
    this.pythonBinary = pythonBinary != null && !pythonBinary.isBlank() ? pythonBinary : "python3";
  }

  @Override
  public boolean isAvailable() {
    if (!enabled) {
      return false;
    }
    try {
      Process process = new ProcessBuilder(pythonBinary, "--version").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("Python availability check interrupted or failed", e);
      return false;
    }
  }

  @Override
  public Path prepareWorkspace(Path workspace, List<String> requirements, String basePythonBinary) {
    String interpreter =
        basePythonBinary != null && !basePythonBinary.isBlank() ? basePythonBinary : pythonBinary;
    Path venvDir = workspace.resolve("venv");
    runProcess(
        workspace,
        List.of(interpreter, "-m", "venv", venvDir.toString()),
        java.util.Map.of(),
        Duration.ofMinutes(2),
        (line, stderr) -> {});

    Path venvPython = venvDir.resolve("bin").resolve("python");
    if (!Files.isExecutable(venvPython)) {
      venvPython = venvDir.resolve("Scripts").resolve("python.exe");
    }
    if (requirements != null && !requirements.isEmpty()) {
      Path requirementsFile = workspace.resolve("requirements.txt");
      try {
        Files.write(
            requirementsFile,
            String.join(System.lineSeparator(), requirements).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new IllegalStateException("Failed to write requirements.txt", e);
      }
      Path pip = venvDir.resolve("bin").resolve("pip");
      if (!Files.isExecutable(pip)) {
        pip = venvDir.resolve("Scripts").resolve("pip.exe");
      }
      runProcess(
          workspace,
          List.of(pip.toString(), "install", "-r", requirementsFile.toString()),
          java.util.Map.of(),
          Duration.ofMinutes(10),
          (line, stderr) -> {});
    }
    return venvPython;
  }

  @Override
  public PythonRunResult run(PythonRunRequest request, PythonLogLineConsumer logConsumer) {
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();
    PythonLogLineConsumer collecting =
        (line, isStderr) -> {
          if (line == null || line.isBlank()) {
            return;
          }
          if (isStderr) {
            stderr.append(line).append('\n');
          } else {
            stdout.append(line).append('\n');
          }
          logConsumer.onLine(line, isStderr);
        };

    try {
      ProcessResult result =
          runProcess(
              request.workspace(),
              request.command(),
              request.environment(),
              request.timeout(),
              collecting);
      return new PythonRunResult(
          result.exitCode(), result.timedOut(), stdout.toString(), stderr.toString());
    } catch (RuntimeException e) {
      log.error("Python process failed", kv("workspace", request.workspace()), e);
      return new PythonRunResult(1, false, stdout.toString(), stderr.toString());
    }
  }

  private ProcessResult runProcess(
      Path cwd,
      List<String> command,
      java.util.Map<String, String> environment,
      Duration timeout,
      PythonLogLineConsumer logConsumer) {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(cwd.toFile());
    if (environment != null && !environment.isEmpty()) {
      builder.environment().putAll(environment);
    }
    builder.redirectErrorStream(false);

    try {
      Process process = builder.start();
      Thread stdoutReader =
          new Thread(
              () -> streamLogs(process.getInputStream(), false, logConsumer), "python-stdout");
      Thread stderrReader =
          new Thread(
              () -> streamLogs(process.getErrorStream(), true, logConsumer), "python-stderr");
      stdoutReader.start();
      stderrReader.start();

      boolean timedOut = false;
      if (timeout == null || timeout.isZero()) {
        process.waitFor();
      } else {
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
          timedOut = true;
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }

      stdoutReader.join(5_000);
      stderrReader.join(5_000);
      int exitCode = process.exitValue();
      if (timedOut) {
        exitCode = 124;
      }
      return new ProcessResult(exitCode, timedOut);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Python subprocess failed: " + command, e);
    }
  }

  private static void streamLogs(
      InputStream stream, boolean stderr, PythonLogLineConsumer consumer) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        consumer.onLine(line, stderr);
      }
    } catch (IOException e) {
      log.debug("Error reading python stream", e);
    }
  }

  private record ProcessResult(int exitCode, boolean timedOut) {}
}
