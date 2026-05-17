package io.pravah.common.domain.resolution;

/**
 * Reference to a HashiCorp Vault secret: {@code vault:path#key}. Resolved at stage execution.
 *
 * <p>Example: {@code vault:secret/data/myapp#password}
 */
public record VaultRef(String path, String key) implements ValueReference {

  public VaultRef {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Vault path is required");
    }
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Vault key is required");
    }
    if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
      throw new IllegalArgumentException("Invalid vault key: " + key);
    }
  }

  @Override
  public String raw() {
    return "vault:" + path + "#" + key;
  }

  @Override
  public boolean isDeferred() {
    return true;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.VAULT;
  }
}
