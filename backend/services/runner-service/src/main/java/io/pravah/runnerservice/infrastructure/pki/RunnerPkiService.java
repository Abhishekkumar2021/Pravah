package io.pravah.runnerservice.infrastructure.pki;

import io.pravah.common.grpc.RunnerCertificateIdentityParser;
import io.pravah.common.vault.HttpVaultPkiClient;
import io.pravah.common.vault.VaultIssuedCertificate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Issues runner mTLS client certificates from Vault PKI on registration (ADR-008). */
@Service
@ConditionalOnProperty(name = "pravah.runner.pki.enabled", havingValue = "true")
public class RunnerPkiService {

  private static final Logger log = LoggerFactory.getLogger(RunnerPkiService.class);

  private final HttpVaultPkiClient pkiClient;
  private final RunnerPkiProperties pkiProperties;

  public RunnerPkiService(HttpVaultPkiClient pkiClient, RunnerPkiProperties pkiProperties) {
    this.pkiClient = pkiClient;
    this.pkiProperties = pkiProperties;
  }

  public Optional<VaultIssuedCertificate> issueRunnerCertificate(UUID tenantId, UUID runnerId) {
    String spiffeUri =
        RunnerCertificateIdentityParser.runnerSpiffeUri(
            pkiProperties.getSpiffeTrustDomain(), tenantId, runnerId);
    String commonName = runnerId.toString();
    try {
      VaultIssuedCertificate issued =
          pkiClient.issue(
              pkiProperties.getIssuePath(), commonName, List.of(spiffeUri), pkiProperties.getTtl());
      log.info(
          "Issued runner mTLS certificate from Vault PKI: runnerId={}, ttl={}",
          runnerId,
          pkiProperties.getTtl());
      return Optional.of(issued);
    } catch (RuntimeException e) {
      log.error("Failed to issue runner certificate from Vault PKI for runnerId={}", runnerId, e);
      throw e;
    }
  }
}
