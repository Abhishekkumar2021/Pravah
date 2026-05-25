package io.pravah.runnerservice.infrastructure.security;

/** Request attributes set by {@link RunnerAgentAuthFilter}. */
public final class RunnerAgentRequestAttributes {

  public static final String RUNNER_ID = "pravah.runner.id";

  private RunnerAgentRequestAttributes() {}
}
