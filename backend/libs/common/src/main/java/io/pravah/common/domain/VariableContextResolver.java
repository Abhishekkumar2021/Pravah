package io.pravah.common.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves final variable values for an execution (US-01.05). */
public final class VariableContextResolver {

  private static final Logger log = LoggerFactory.getLogger(VariableContextResolver.class);

  private static final Pattern BUILTIN_REFERENCE =
      Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

  private static final DateTimeFormatter EXECUTION_DATE =
      DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

  private VariableContextResolver() {}

  /**
   * Builds substitution context: defaults → environment overrides → runtime parameters (wins).
   *
   * @param environmentName from {@link PipelineVariable#ENVIRONMENT_PARAMETER} runtime param, may
   *     be null
   */
  public static Map<String, Object> resolve(
      Map<String, Object> definition,
      String environmentName,
      Map<String, Object> runtimeParameters,
      UUID pipelineId,
      int pipelineVersion,
      UUID executionId,
      Instant executionTime) {

    Map<String, PipelineVariable> variables =
        PipelineVariablesParser.parseVariables(definition.get("variables"));
    Map<String, Map<String, Object>> environments =
        PipelineVariablesParser.parseEnvironments(definition.get("environments"));

    Map<String, Object> builtins =
        Map.of(
            "execution_date",
            EXECUTION_DATE.format(executionTime),
            "execution_id",
            executionId != null ? executionId.toString() : "",
            "pipeline_id",
            pipelineId.toString(),
            "pipeline_version",
            pipelineVersion);

    Map<String, Object> resolved = new LinkedHashMap<>();

    for (PipelineVariable variable : variables.values()) {
      Object value = resolveDefault(variable, builtins);
      if (value != null) {
        resolved.put(variable.name(), value);
      } else if (variable.required()) {
        throw new IllegalArgumentException(
            "required variable '" + variable.name() + "' has no value");
      }
    }

    if (environmentName != null && !environmentName.isBlank()) {
      Map<String, Object> envOverrides = environments.get(environmentName);
      if (envOverrides == null) {
        throw new IllegalArgumentException("unknown environment: " + environmentName);
      }
      applyOverrides(resolved, variables, envOverrides);
    }

    Map<String, Object> runtime = stripEnvironmentKey(runtimeParameters);
    applyOverrides(resolved, variables, runtime);

    for (PipelineVariable variable : variables.values()) {
      variable.validateValue(resolved.get(variable.name()));
    }

    return Map.copyOf(resolved);
  }

  private static Map<String, Object> stripEnvironmentKey(Map<String, Object> runtimeParameters) {
    if (runtimeParameters == null || runtimeParameters.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> copy = new LinkedHashMap<>(runtimeParameters);
    copy.remove(PipelineVariable.ENVIRONMENT_PARAMETER);
    return copy;
  }

  private static void applyOverrides(
      Map<String, Object> resolved,
      Map<String, PipelineVariable> variables,
      Map<String, Object> overrides) {
    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
      PipelineVariable variable = variables.get(entry.getKey());
      if (variable == null) {
        log.debug("Ignoring undeclared runtime parameter: {}", entry.getKey());
        continue;
      }
      Object coerced = variable.coerceRuntimeValue(entry.getValue());
      variable.validateValue(coerced);
      if (coerced != null) {
        resolved.put(entry.getKey(), coerced);
      } else {
        resolved.remove(entry.getKey());
      }
    }
  }

  private static Object resolveDefault(PipelineVariable variable, Map<String, Object> builtins) {
    Object defaultValue = variable.defaultValue();
    if (defaultValue == null) {
      return null;
    }
    if (defaultValue instanceof String s) {
      return substituteBuiltins(s, builtins);
    }
    Object coerced = variable.coerceRuntimeValue(defaultValue);
    variable.validateValue(coerced);
    return coerced;
  }

  static String substituteBuiltins(String template, Map<String, Object> builtins) {
    Matcher matcher = BUILTIN_REFERENCE.matcher(template);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      if (PipelineVariable.BUILTIN_NAMES.contains(name)) {
        Object value = builtins.get(name);
        matcher.appendReplacement(
            out, Matcher.quoteReplacement(value != null ? value.toString() : ""));
      }
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
