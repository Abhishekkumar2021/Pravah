package io.pravah.common.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Resolves pipeline definition variables for execution materialization (US-01.05). */
public final class PipelineDefinitionResolver {

  private PipelineDefinitionResolver() {}

  public static Map<String, Object> resolveForExecution(
      Map<String, Object> definition,
      Map<String, Object> runtimeParameters,
      UUID pipelineId,
      int pipelineVersion,
      UUID executionId,
      Instant executionTime) {

    if (definition == null || definition.isEmpty()) {
      return definition == null ? Map.of() : definition;
    }

    String environment = extractEnvironment(runtimeParameters);
    Map<String, Object> context =
        VariableContextResolver.resolve(
            definition,
            environment,
            runtimeParameters,
            pipelineId,
            pipelineVersion,
            executionId,
            executionTime);
    return VariableSubstitutor.substitute(definition, context);
  }

  public static String extractEnvironment(Map<String, Object> runtimeParameters) {
    if (runtimeParameters == null) {
      return null;
    }
    Object env = runtimeParameters.get(PipelineVariable.ENVIRONMENT_PARAMETER);
    return env == null ? null : env.toString();
  }
}
