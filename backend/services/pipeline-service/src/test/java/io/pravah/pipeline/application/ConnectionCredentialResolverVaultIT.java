package io.pravah.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.domain.resolution.VaultResolverProvider;
import io.pravah.common.vault.HttpVaultKvClient;
import io.pravah.common.vault.VaultSettings;
import io.pravah.test.containers.VaultContainerExtension;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for {@code vault:path#key} credential resolution against real Vault (ADR-007).
 */
@ExtendWith(VaultContainerExtension.class)
class ConnectionCredentialResolverVaultIT {

  private ConnectionCredentialResolver resolver() {
    VaultSettings settings =
        new VaultSettings(
            true,
            VaultContainerExtension.getAddress(),
            "token",
            VaultContainerExtension.getRootToken(),
            "",
            "kubernetes",
            "/var/run/secrets/kubernetes.io/serviceaccount/token",
            Duration.ofSeconds(10));
    return new ConnectionCredentialResolver(
        new VaultResolverProvider(new HttpVaultKvClient(settings)));
  }

  @Test
  void resolveReference_vaultPath_returnsSecretValue() {
    assertThat(resolver().resolveReference(VaultContainerExtension.demoVaultReference()))
        .isEqualTo(VaultContainerExtension.demoPassword());
  }

  @Test
  void resolvePassword_fromConfigCredentials_resolvesVaultRef() {
    Map<String, Object> config =
        Map.of("credentials", Map.of("password", VaultContainerExtension.demoVaultReference()));

    assertThat(resolver().resolvePassword(config))
        .isEqualTo(VaultContainerExtension.demoPassword());
  }
}
