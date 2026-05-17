package io.pravah.common.domain.resolution;

/** Reference to a tenant secret: {@code ${secret.name}}. Resolved at stage execution. */
public record SecretRef(String name) implements ValueReference {

  public SecretRef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Secret name is required");
    }
    if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
      throw new IllegalArgumentException("Invalid secret name: " + name);
    }
  }

  @Override
  public String raw() {
    return "${secret." + name + "}";
  }

  @Override
  public boolean isDeferred() {
    return true;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.SECRET;
  }
}
