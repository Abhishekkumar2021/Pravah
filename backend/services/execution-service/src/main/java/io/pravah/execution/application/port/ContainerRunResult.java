package io.pravah.execution.application.port;

/** Result of a container run. */
public record ContainerRunResult(int exitCode, boolean timedOut) {}
