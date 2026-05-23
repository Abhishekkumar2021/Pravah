package io.pravah.runnerservice.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.grpc.GrpcTlsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerGrpcTlsPropertiesTest {

  @TempDir Path tempDir;

  @Test
  void toConfig_whenDisabled_returnsDisabled() {
    RunnerGrpcTlsProperties props = new RunnerGrpcTlsProperties();
    assertThat(props.toConfig()).isEqualTo(GrpcTlsConfig.disabled());
  }

  @Test
  void toConfig_whenEnabled_mapsPathsAndClientCa(@TempDir Path dir) throws Exception {
    Path cert = dir.resolve("tls.crt");
    Path key = dir.resolve("tls.key");
    Path ca = dir.resolve("ca.crt");
    Files.writeString(cert, "cert");
    Files.writeString(key, "key");
    Files.writeString(ca, "ca");

    RunnerGrpcTlsProperties props = new RunnerGrpcTlsProperties();
    props.setEnabled(true);
    props.setCertChain(cert.toString());
    props.setPrivateKey(key.toString());
    props.setClientCa(ca.toString());

    GrpcTlsConfig config = props.toConfig();
    assertThat(config.enabled()).isTrue();
    assertThat(config.certChainFile()).isEqualTo(cert);
    assertThat(config.privateKeyFile()).isEqualTo(key);
    assertThat(config.clientCaFile()).isEqualTo(ca);
  }
}
