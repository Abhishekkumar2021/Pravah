package io.pravah.execution.application.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Request to run a Python stage in an isolated workspace (US-02.15). */
public record PythonRunRequest(
    Path workspace,
    Path pythonExecutable,
    List<String> command,
    Map<String, String> environment,
    Duration timeout) {}
