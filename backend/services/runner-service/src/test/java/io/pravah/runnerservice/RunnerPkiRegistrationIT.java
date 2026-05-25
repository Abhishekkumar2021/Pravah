package io.pravah.runnerservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.grpc.RunnerCertificateIdentityParser;
import io.pravah.common.vault.VaultIssuedCertificate;
import io.pravah.runnerservice.infrastructure.grpc.RunnerGrpcServerLifecycle;
import io.pravah.runnerservice.service.RunnerService;
import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.containers.VaultContainerExtension;
import io.pravah.test.security.TestSecurityConfiguration;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** End-to-end: Vault PKI issues runner client cert on registration (ADR-008). */
@ExtendWith({PostgresContainerExtension.class, VaultContainerExtension.class})
@Import({TestSecurityConfiguration.class, RunnerPkiRegistrationIT.NoGrpcServerConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=localhost:29092",
      "grpc.server.port=0",
      "pravah.security.jwt.jwks-url=http://127.0.0.1:9/jwks",
      "pravah.internal-service.secret=test-secret",
      "pravah.execution-service.base-url=http://127.0.0.1:8084",
      "pravah.vault.enabled=true",
      "pravah.vault.auth.method=token",
      "pravah.runner.grpc.tls.enabled=true",
      "pravah.runner.pki.enabled=true",
      "pravah.runner.pki.issue-path=pki/issue/runner",
      "pravah.runner.pki.ttl=1h",
      "pravah.runner.pki.spiffe-trust-domain=pravah.local"
    })
class RunnerPkiRegistrationIT {

  @Autowired private RunnerService runnerService;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
    registry.add("pravah.vault.address", VaultContainerExtension::getAddress);
    registry.add("pravah.vault.auth.token", VaultContainerExtension::getRootToken);
  }

  @Test
  void registerRunner_issuesSpiffeMtlsCertificate() throws Exception {
    UUID tenantId = UUID.randomUUID();
    var request =
        new RunnerService.RegisterRequest(
            "pki-it-runner", "0.1.0", Map.of("env", "it"), 2, List.of("shell"), 512L, 1, null);

    RunnerService.RegisterResult result = runnerService.registerRunner(tenantId, request);

    assertThat(result.mtlsCertificate()).isPresent();
    VaultIssuedCertificate mtls = result.mtlsCertificate().orElseThrow();
    assertThat(mtls.certificatePem()).contains("BEGIN CERTIFICATE");
    assertThat(mtls.privateKeyPem()).contains("PRIVATE KEY");

    X509Certificate x509 = parseCertificate(mtls.certificatePem());
    var identity = RunnerCertificateIdentityParser.parse(x509).orElseThrow();
    assertThat(identity.runnerId()).isEqualTo(result.runnerId());
    assertThat(identity.tenantId()).contains(tenantId);
  }

  private static X509Certificate parseCertificate(String pem) throws Exception {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate)
        factory.generateCertificate(
            new ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  @TestConfiguration
  static class NoGrpcServerConfig {

    @Bean
    @Primary
    RunnerGrpcServerLifecycle runnerGrpcServerLifecycle() {
      return Mockito.mock(RunnerGrpcServerLifecycle.class);
    }
  }
}
