package io.pravah.common.grpc;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrpcTlsConfigTest {

  @TempDir Path tempDir;

  @Test
  void disabled_doesNotValidate() {
    assertThatCode(GrpcTlsConfig.disabled()::validateServer).doesNotThrowAnyException();
    assertThatCode(GrpcTlsConfig.disabled()::validateClient).doesNotThrowAnyException();
  }

  @Test
  void validateServer_requiresReadableFiles(@TempDir Path dir) throws Exception {
    Path cert = dir.resolve("server.crt");
    Path key = dir.resolve("server.key");
    Files.writeString(cert, "cert");
    Files.writeString(key, "key");

    assertThatCode(() -> GrpcTlsConfig.server(cert, key, null).validateServer())
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () -> GrpcTlsConfig.server(cert, key, dir.resolve("missing-ca")).validateServer())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("client CA");
  }

  @Test
  void validateClient_requiresTrustAndOptionalClientCert(@TempDir Path dir) throws Exception {
    Path trust = dir.resolve("ca.crt");
    Files.writeString(trust, "ca");

    assertThatCode(() -> GrpcTlsConfig.client(trust, null, null).validateClient())
        .doesNotThrowAnyException();

    Path clientCert = dir.resolve("client.crt");
    Path clientKey = dir.resolve("client.key");
    Files.writeString(clientCert, "cc");
    Files.writeString(clientKey, "ck");

    assertThatCode(() -> GrpcTlsConfig.client(trust, clientCert, clientKey).validateClient())
        .doesNotThrowAnyException();
  }
}
