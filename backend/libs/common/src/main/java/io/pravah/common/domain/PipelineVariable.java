package io.pravah.common.domain;

import java.util.Locale;
import java.util.Set;

/** Declared pipeline variable (US-01.05). */
public record PipelineVariable(
    String name, VariableType type, Object defaultValue, boolean required) {

  public static final String ENVIRONMENT_PARAMETER = "environment";

  public enum VariableType {
    STRING,
    NUMBER,
    BOOLEAN;

    public static VariableType parse(String raw) {
      if (raw == null || raw.isBlank()) {
        return STRING;
      }
      return switch (raw.toLowerCase(Locale.ROOT)) {
        case "string" -> STRING;
        case "number", "integer", "int" -> NUMBER;
        case "boolean", "bool" -> BOOLEAN;
        default ->
            throw new IllegalArgumentException("variable type must be string, number, or boolean");
      };
    }
  }

  public PipelineVariable {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("variable name is required");
    }
    if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
      throw new IllegalArgumentException("variable name must be alphanumeric: " + name);
    }
  }

  public void validateValue(Object value) {
    if (value == null) {
      if (required) {
        throw new IllegalArgumentException("variable '" + name + "' is required");
      }
      return;
    }
    switch (type) {
      case STRING -> {
        if (!(value instanceof String)) {
          throw new IllegalArgumentException("variable '" + name + "' must be a string");
        }
      }
      case NUMBER -> {
        if (!(value instanceof Number)) {
          throw new IllegalArgumentException("variable '" + name + "' must be a number");
        }
      }
      case BOOLEAN -> {
        if (!(value instanceof Boolean)) {
          throw new IllegalArgumentException("variable '" + name + "' must be a boolean");
        }
      }
    }
  }

  public Object coerceRuntimeValue(Object raw) {
    if (raw == null) {
      return null;
    }
    return switch (type) {
      case STRING -> raw.toString();
      case NUMBER -> {
        if (raw instanceof Number n) {
          yield n;
        }
        if (raw instanceof String s) {
          try {
            yield Double.parseDouble(s);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("variable '" + name + "' must be a number");
          }
        }
        throw new IllegalArgumentException("variable '" + name + "' must be a number");
      }
      case BOOLEAN -> {
        if (raw instanceof Boolean b) {
          yield b;
        }
        if (raw instanceof String s) {
          String lower = s.toLowerCase(Locale.ROOT);
          if ("true".equals(lower)) {
            yield true;
          }
          if ("false".equals(lower)) {
            yield false;
          }
          throw new IllegalArgumentException(
              "variable '" + name + "' must be 'true' or 'false', got: " + s);
        }
        throw new IllegalArgumentException("variable '" + name + "' must be a boolean");
      }
    };
  }

  public static final Set<String> BUILTIN_NAMES =
      Set.of("execution_date", "execution_id", "pipeline_id", "pipeline_version");
}
