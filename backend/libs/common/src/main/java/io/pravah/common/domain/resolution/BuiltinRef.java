package io.pravah.common.domain.resolution;

import java.util.Set;

/**
 * Reference to a built-in variable: {@code ${execution_date}}, {@code ${execution_id}}, etc.
 * Resolved at execution start.
 */
public record BuiltinRef(String name) implements ValueReference {

  public static final Set<String> SUPPORTED_BUILTINS =
      Set.of("execution_date", "execution_id", "pipeline_id", "pipeline_version");

  public BuiltinRef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Builtin name is required");
    }
    if (!SUPPORTED_BUILTINS.contains(name)) {
      throw new IllegalArgumentException(
          "Unknown builtin: " + name + ". Supported: " + SUPPORTED_BUILTINS);
    }
  }

  @Override
  public String raw() {
    return "${" + name + "}";
  }

  @Override
  public boolean isDeferred() {
    return false;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.BUILTIN;
  }
}
