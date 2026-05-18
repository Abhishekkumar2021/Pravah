package io.pravah.execution.application.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs Python stages in a per-job virtual environment (US-02.15).
 *
 * <p>Implementations create a venv under {@code workspace}, optionally install requirements, then
 * execute the requested command.
 */
public interface PythonRuntime {

  /** Whether Python execution is enabled and the configured interpreter is reachable. */
  boolean isAvailable();

  /**
   * Creates {@code workspace/venv} and installs optional requirements.
   *
   * @return path to the venv's {@code python} executable
   */
  Path prepareWorkspace(Path workspace, List<String> requirements, String basePythonBinary);

  PythonRunResult run(PythonRunRequest request, PythonLogLineConsumer logConsumer);
}
