package io.pravah.common.domain.resolution;

/** Reference to a pipeline variable: {@code ${var.name}}. Resolved at execution start. */
public record VariableRef(String name) implements ValueReference {

  public VariableRef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Variable name is required");
    }
    if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
      throw new IllegalArgumentException("Invalid variable name: " + name);
    }
  }

  @Override
  public String raw() {
    return "${var." + name + "}";
  }

  @Override
  public boolean isDeferred() {
    return false;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.VARIABLE;
  }
}
