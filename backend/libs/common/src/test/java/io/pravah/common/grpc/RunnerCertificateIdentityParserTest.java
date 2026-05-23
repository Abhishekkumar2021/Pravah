package io.pravah.common.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunnerCertificateIdentityParserTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID RUNNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Test
  void parseSpiffeUri_tenantAndRunner() {
    Optional<RunnerCertificateIdentity> identity =
        RunnerCertificateIdentityParser.parseSpiffeUri(
            "spiffe://pravah.local/tenant/" + TENANT_ID + "/runner/" + RUNNER_ID);

    assertThat(identity).isPresent();
    assertThat(identity.get().runnerId()).isEqualTo(RUNNER_ID);
    assertThat(identity.get().tenantId()).contains(TENANT_ID);
  }

  @Test
  void parseSpiffeUri_runnerOnly() {
    Optional<RunnerCertificateIdentity> identity =
        RunnerCertificateIdentityParser.parseSpiffeUri(
            "spiffe://pravah.cluster.local/runner/" + RUNNER_ID);

    assertThat(identity).isPresent();
    assertThat(identity.get().runnerId()).isEqualTo(RUNNER_ID);
    assertThat(identity.get().tenantId()).isEmpty();
  }

  @Test
  void parse_rejectsUnknownUri() {
    assertThat(RunnerCertificateIdentityParser.parseSpiffeUri("spiffe://other/service/foo"))
        .isEmpty();
  }

  @Test
  void parse_readsUriFromCertificateSan() throws Exception {
    X509Certificate cert = mock(X509Certificate.class);
    when(cert.getSubjectAlternativeNames())
        .thenReturn(
            List.of(
                List.of(6, "spiffe://pravah.local/tenant/" + TENANT_ID + "/runner/" + RUNNER_ID)));

    Optional<RunnerCertificateIdentity> identity = RunnerCertificateIdentityParser.parse(cert);

    assertThat(identity).isPresent();
    assertThat(identity.get().runnerId()).isEqualTo(RUNNER_ID);
  }

  @Test
  void parse_fallsBackToSubjectCnUuid() throws Exception {
    X509Certificate cert = mock(X509Certificate.class);
    when(cert.getSubjectAlternativeNames()).thenReturn(null);
    when(cert.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=" + RUNNER_ID + ",O=Pravah"));

    Optional<RunnerCertificateIdentity> identity = RunnerCertificateIdentityParser.parse(cert);

    assertThat(identity).isPresent();
    assertThat(identity.get().runnerId()).isEqualTo(RUNNER_ID);
    assertThat(identity.get().tenantId()).isEmpty();
  }
}
