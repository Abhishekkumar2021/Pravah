package io.pravah.common.vault;

/**
 * Reads secret fields from Vault KV v2 (ADR-007).
 *
 * <p>Path format matches {@code vault:path#key} references — e.g. path {@code secret/data/myapp},
 * field {@code password} → {@code GET /v1/secret/data/myapp}.
 */
public interface VaultKvReader {

  /** No-op reader used when Vault is disabled. */
  static VaultKvReader disabled() {
    return (path, key) -> {
      throw new VaultException(
          "Vault integration is disabled. Set PRAVAH_VAULT_ENABLED=true and configure VAULT_ADDR.");
    };
  }

  /**
   * @param apiPath mount path including {@code /data/} segment (e.g. {@code secret/data/myapp})
   * @param fieldKey key inside the secret payload
   * @return secret value as string
   */
  String readField(String apiPath, String fieldKey);
}
