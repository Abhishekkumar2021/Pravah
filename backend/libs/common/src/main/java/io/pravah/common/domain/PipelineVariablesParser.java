package io.pravah.common.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and validates pipeline {@code variables} and {@code environments} blocks (US-01.05). */
public final class PipelineVariablesParser {

  private static final Pattern VAR_REFERENCE =
      Pattern.compile("\\$\\{var\\.([a-zA-Z_][a-zA-Z0-9_]*)}");

  private PipelineVariablesParser() {}

  public static Map<String, PipelineVariable> parseVariables(Object raw) {
    if (raw == null) {
      return Map.of();
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("variables must be a mapping");
    }
    Map<String, PipelineVariable> variables = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String name = entry.getKey().toString();
      if (!(entry.getValue() instanceof Map<?, ?> spec)) {
        throw new IllegalArgumentException("variable '" + name + "' must be a mapping");
      }
      variables.put(name, parseVariableSpec(name, spec));
    }
    return Map.copyOf(variables);
  }

  public static Map<String, Map<String, Object>> parseEnvironments(Object raw) {
    if (raw == null) {
      return Map.of();
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("environments must be a mapping");
    }
    Map<String, Map<String, Object>> environments = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String envName = entry.getKey().toString();
      if (!(entry.getValue() instanceof Map<?, ?> overrides)) {
        throw new IllegalArgumentException("environment '" + envName + "' must be a mapping");
      }
      Map<String, Object> coerced = new LinkedHashMap<>();
      for (Map.Entry<?, ?> override : overrides.entrySet()) {
        coerced.put(override.getKey().toString(), override.getValue());
      }
      environments.put(envName, Map.copyOf(coerced));
    }
    return Map.copyOf(environments);
  }

  public static void validateDefinition(Map<String, Object> definition) {
    if (definition == null) {
      return;
    }
    Map<String, PipelineVariable> variables = parseVariables(definition.get("variables"));
    Map<String, Map<String, Object>> environments =
        parseEnvironments(definition.get("environments"));

    for (Map.Entry<String, Map<String, Object>> env : environments.entrySet()) {
      for (String key : env.getValue().keySet()) {
        if (!variables.containsKey(key)) {
          throw new IllegalArgumentException(
              "environment '" + env.getKey() + "' references undefined variable '" + key + "'");
        }
      }
    }

    validateReferences(definition, variables.keySet());
  }

  public static void validateReferences(Map<String, Object> definition, Set<String> declaredNames) {
    Set<String> referenced = collectVarReferences(definition);
    for (String name : referenced) {
      if (!declaredNames.contains(name)) {
        throw new IllegalArgumentException("undefined variable reference: var." + name);
      }
    }
  }

  public static Set<String> collectVarReferences(Map<String, Object> definition) {
    Set<String> names = new LinkedHashSet<>();
    collectFromObject(definition, names);
    return names;
  }

  private static void collectFromObject(Object node, Set<String> names) {
    if (node instanceof String s) {
      Matcher matcher = VAR_REFERENCE.matcher(s);
      while (matcher.find()) {
        names.add(matcher.group(1));
      }
    } else if (node instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        collectFromObject(value, names);
      }
    } else if (node instanceof List<?> list) {
      for (Object item : list) {
        collectFromObject(item, names);
      }
    }
  }

  private static PipelineVariable parseVariableSpec(String name, Map<?, ?> spec) {
    PipelineVariable.VariableType type =
        PipelineVariable.VariableType.parse(stringField(spec, "type", "string"));
    boolean required = booleanField(spec, "required", false);
    Object defaultValue = spec.get("default");
    PipelineVariable variable = new PipelineVariable(name, type, defaultValue, required);
    if (defaultValue != null) {
      Object coerced = variable.coerceRuntimeValue(defaultValue);
      variable.validateValue(coerced);
    }
    return variable;
  }

  private static String stringField(Map<?, ?> map, String key, String defaultValue) {
    Object v = map.get(key);
    return v == null ? defaultValue : v.toString();
  }

  private static boolean booleanField(Map<?, ?> map, String key, boolean defaultValue) {
    Object v = map.get(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(v.toString());
  }
}
