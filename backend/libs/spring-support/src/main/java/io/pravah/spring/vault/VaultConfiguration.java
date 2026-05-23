package io.pravah.spring.vault;

import io.pravah.common.domain.resolution.VaultResolverProvider;
import io.pravah.common.vault.HttpVaultKvClient;
import io.pravah.common.vault.HttpVaultTransitClient;
import io.pravah.common.vault.VaultKvReader;
import io.pravah.common.vault.VaultSettings;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultConfiguration {

  @Bean
  @ConditionalOnProperty(name = "pravah.vault.enabled", havingValue = "true")
  HttpVaultTransitClient vaultTransitClient(VaultProperties properties) {
    return new HttpVaultTransitClient(vaultSettings(properties));
  }

  @Bean
  @ConditionalOnProperty(name = "pravah.vault.enabled", havingValue = "true")
  VaultKvReader vaultKvReader(VaultProperties properties) {
    return new HttpVaultKvClient(vaultSettings(properties));
  }

  private static VaultSettings vaultSettings(VaultProperties properties) {
    return new VaultSettings(
        true,
        properties.address(),
        properties.auth().method(),
        properties.auth().token(),
        properties.auth().kubernetes().role(),
        properties.auth().kubernetes().mountPath(),
        System.getenv()
            .getOrDefault(
                "PRAVAH_VAULT_K8S_SA_TOKEN_PATH",
                "/var/run/secrets/kubernetes.io/serviceaccount/token"),
        Duration.ofSeconds(properties.requestTimeoutSeconds()));
  }

  @Bean
  VaultResolverProvider vaultResolverProvider(
      org.springframework.beans.factory.ObjectProvider<VaultKvReader> vaultKvReader) {
    return new VaultResolverProvider(vaultKvReader.getIfAvailable(VaultKvReader::disabled));
  }
}
