package io.pravah.runnerservice.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.pravah.common.grpc.GrpcTlsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerGrpcServerTlsTest {

  @TempDir Path tempDir;

  @Test
  void clientAuthMode_whenClientCaConfigured_isOptionalForPkiBootstrap() throws Exception {
    Path cert = tempDir.resolve("server.crt");
    Path key = tempDir.resolve("server.key");
    Path ca = tempDir.resolve("ca.crt");
    Files.writeString(cert, "cert");
    Files.writeString(key, "key");
    Files.writeString(ca, "ca");

    GrpcTlsConfig tls = GrpcTlsConfig.server(cert, key, ca);

    assertThat(RunnerGrpcServerTls.clientAuthMode(tls)).isEqualTo(ClientAuth.OPTIONAL);
  }

  @Test
  void clientAuthMode_whenNoClientCa_isNone() throws Exception {
    Path cert = tempDir.resolve("server.crt");
    Path key = tempDir.resolve("server.key");
    Files.writeString(cert, "cert");
    Files.writeString(key, "key");

    GrpcTlsConfig tls = GrpcTlsConfig.server(cert, key, null);

    assertThat(RunnerGrpcServerTls.clientAuthMode(tls)).isEqualTo(ClientAuth.NONE);
  }
}
