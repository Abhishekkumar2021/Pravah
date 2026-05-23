package io.pravah.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.grpc.GrpcTlsConfig;
import io.pravah.proto.runner.RunnerMtlsCertificate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerMtlsMaterializerTest {

  @Test
  void materialize_writesPemFilesAndReturnsClientTlsConfig(@TempDir Path workDir) throws Exception {
    RunnerMtlsCertificate mtls =
        RunnerMtlsCertificate.newBuilder()
            .setCertificatePem("-----BEGIN CERTIFICATE-----\ncert\n-----END CERTIFICATE-----")
            .setPrivateKeyPem("-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----")
            .setCaChainPem("-----BEGIN CERTIFICATE-----\nca\n-----END CERTIFICATE-----")
            .build();

    GrpcTlsConfig config =
        RunnerMtlsMaterializer.materialize(workDir, mtls, GrpcTlsConfig.disabled());

    assertThat(config.enabled()).isTrue();
    assertThat(config.clientCertFile()).exists();
    assertThat(config.clientKeyFile()).exists();
    assertThat(config.trustCertFile()).exists();
    assertThat(Files.readString(config.clientCertFile())).contains("BEGIN CERTIFICATE");
    if (Files.getFileStore(config.clientKeyFile()).supportsFileAttributeView("posix")) {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(config.clientKeyFile());
      assertThat(perms).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
  }
}
