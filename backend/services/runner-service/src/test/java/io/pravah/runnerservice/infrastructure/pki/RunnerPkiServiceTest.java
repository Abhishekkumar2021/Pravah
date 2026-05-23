package io.pravah.runnerservice.infrastructure.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.vault.HttpVaultPkiClient;
import io.pravah.common.vault.VaultIssuedCertificate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunnerPkiServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID RUNNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private HttpVaultPkiClient pkiClient;

  private RunnerPkiService service;

  @BeforeEach
  void setUp() {
    RunnerPkiProperties properties = new RunnerPkiProperties();
    properties.setIssuePath("pki/issue/runner");
    properties.setTtl("168h");
    properties.setSpiffeTrustDomain("pravah.local");
    service = new RunnerPkiService(pkiClient, properties);
  }

  @Test
  void issueRunnerCertificate_usesSpiffeUriSan() {
    when(pkiClient.issue(
            eq("pki/issue/runner"),
            eq(RUNNER_ID.toString()),
            org.mockito.ArgumentMatchers.anyList(),
            eq("168h")))
        .thenReturn(new VaultIssuedCertificate("cert", "key", "ca"));

    assertThat(service.issueRunnerCertificate(TENANT_ID, RUNNER_ID)).isPresent();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> sansCaptor = ArgumentCaptor.forClass(List.class);
    verify(pkiClient)
        .issue(eq("pki/issue/runner"), eq(RUNNER_ID.toString()), sansCaptor.capture(), eq("168h"));
    assertThat(sansCaptor.getValue())
        .containsExactly("spiffe://pravah.local/tenant/" + TENANT_ID + "/runner/" + RUNNER_ID);
  }
}
