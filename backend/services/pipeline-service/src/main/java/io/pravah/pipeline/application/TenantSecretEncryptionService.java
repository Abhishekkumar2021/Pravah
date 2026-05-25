package io.pravah.pipeline.application;

import io.pravah.common.vault.HttpVaultTransitClient;
import io.pravah.common.vault.VaultTransitKeyNames;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Encrypts and decrypts tenant secret values via Vault Transit (ADR-007). */
@Component
@ConditionalOnProperty(name = "pravah.vault.enabled", havingValue = "true")
public class TenantSecretEncryptionService {

  private final HttpVaultTransitClient transitClient;

  public TenantSecretEncryptionService(HttpVaultTransitClient transitClient) {
    this.transitClient = Objects.requireNonNull(transitClient, "transitClient");
  }

  public String encryptForTenant(UUID tenantId, String plaintext) {
    transitClient.ensureTenantKey(tenantId);
    String keyName = VaultTransitKeyNames.tenantKey(tenantId);
    return transitClient.encrypt(keyName, plaintext);
  }

  public String decrypt(String transitKeyName, String ciphertext) {
    return transitClient.decrypt(transitKeyName, ciphertext);
  }

  public String tenantKeyName(UUID tenantId) {
    return VaultTransitKeyNames.tenantKey(tenantId);
  }
}
