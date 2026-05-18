package io.pravah.execution.application.port;

/** Consumes stdout/stderr lines from a Python subprocess. */
@FunctionalInterface
public interface PythonLogLineConsumer {

  void onLine(String line, boolean stderr);
}
