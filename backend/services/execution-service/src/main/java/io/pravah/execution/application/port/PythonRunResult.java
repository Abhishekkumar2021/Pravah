package io.pravah.execution.application.port;

/** Result of a Python subprocess run. */
public record PythonRunResult(int exitCode, boolean timedOut, String stdout, String stderr) {}
