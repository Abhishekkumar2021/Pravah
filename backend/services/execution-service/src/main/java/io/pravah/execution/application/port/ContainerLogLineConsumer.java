package io.pravah.execution.application.port;

/** Consumes stdout/stderr lines from a running container. */
@FunctionalInterface
public interface ContainerLogLineConsumer {

  void onLine(String line, boolean stderr);
}
