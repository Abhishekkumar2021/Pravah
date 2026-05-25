package io.pravah.runnerservice.infrastructure.pki;

import io.pravah.common.vault.HttpVaultPkiClient;
import io.pravah.common.vault.VaultSettings;
import io.pravah.runnerservice.infrastructure.grpc.RunnerGrpcTlsProperties;
import io.pravah.spring.vault.VaultProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pravah.runner.pki.enabled", havingValue = "true")
public class RunnerPkiVaultConfiguration {

  @Bean
  HttpVaultPkiClient runnerVaultPkiClient(
      VaultProperties vaultProperties, RunnerGrpcTlsProperties grpcTlsProperties) {
    if (!vaultProperties.enabled()) {
      throw new IllegalStateException(
          "pravah.runner.pki.enabled requires pravah.vault.enabled=true");
    }
    if (!grpcTlsProperties.isEnabled()) {
      throw new IllegalStateException(
          "pravah.runner.pki.enabled requires pravah.runner.grpc.tls.enabled=true");
    }
    VaultSettings settings =
        new VaultSettings(
            true,
            vaultProperties.address(),
            vaultProperties.auth().method(),
            vaultProperties.auth().token(),
            vaultProperties.auth().kubernetes().role(),
            vaultProperties.auth().kubernetes().mountPath(),
            System.getenv()
                .getOrDefault(
                    "PRAVAH_VAULT_K8S_SA_TOKEN_PATH",
                    "/var/run/secrets/kubernetes.io/serviceaccount/token"),
            Duration.ofSeconds(vaultProperties.requestTimeoutSeconds()));
    return new HttpVaultPkiClient(settings);
  }
}
