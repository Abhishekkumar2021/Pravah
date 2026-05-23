package io.pravah.common.vault;

import java.time.Duration;
import java.util.Objects;

/**
 * Vault connectivity settings (ADR-007). Immutable; built from env / Spring properties.
 *
 * @param enabled when false, {@link VaultKvReader#disabled()} is used
 * @param address Vault API base URL (e.g. {@code http://vault:8200})
 * @param authMethod {@code token} or {@code kubernetes}
 * @param token static token (dev/local only)
 * @param kubernetesRole Vault Kubernetes auth role name
 * @param kubernetesMountPath auth mount path (default {@code kubernetes})
 * @param serviceAccountTokenPath path to projected SA JWT
 * @param requestTimeout HTTP timeout per Vault call
 */
public record VaultSettings(
    boolean enabled,
    String address,
    String authMethod,
    String token,
    String kubernetesRole,
    String kubernetesMountPath,
    String serviceAccountTokenPath,
    Duration requestTimeout) {

  public VaultSettings {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(authMethod, "authMethod");
    Objects.requireNonNull(kubernetesMountPath, "kubernetesMountPath");
    Objects.requireNonNull(serviceAccountTokenPath, "serviceAccountTokenPath");
    Objects.requireNonNull(requestTimeout, "requestTimeout");
    if (enabled && address.isBlank()) {
      throw new IllegalArgumentException("Vault address is required when enabled");
    }
  }

  public static VaultSettings disabled() {
    return new VaultSettings(
        false,
        "http://localhost:8200",
        "token",
        "",
        "",
        "kubernetes",
        "/var/run/secrets/kubernetes.io/serviceaccount/token",
        Duration.ofSeconds(10));
  }

  public boolean usesKubernetesAuth() {
    return "kubernetes".equalsIgnoreCase(authMethod);
  }
}
