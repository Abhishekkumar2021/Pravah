package io.pravah.runner;

import io.pravah.common.grpc.GrpcTlsConfig;
import io.pravah.proto.runner.RunnerMtlsCertificate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/** Persists runner mTLS PEM material from registration (Vault PKI). */
final class RunnerMtlsMaterializer {

  private RunnerMtlsMaterializer() {}

  static GrpcTlsConfig materialize(
      Path workDir, RunnerMtlsCertificate mtls, GrpcTlsConfig currentTls) throws IOException {
    Path dir = workDir.resolve(".pravah").resolve("mtls");
    Files.createDirectories(dir);
    writePem(dir.resolve("client.crt"), mtls.getCertificatePem());
    writePem(dir.resolve("client.key"), mtls.getPrivateKeyPem());
    Path trustPath = dir.resolve("ca.crt");
    if (!mtls.getCaChainPem().isBlank()) {
      writePem(trustPath, mtls.getCaChainPem());
    } else if (currentTls.trustCertFile() != null) {
      trustPath = currentTls.trustCertFile();
    }
    return GrpcTlsConfig.client(trustPath, dir.resolve("client.crt"), dir.resolve("client.key"));
  }

  private static void writePem(Path path, String pem) throws IOException {
    Files.writeString(
        path,
        pem,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX filesystems (e.g. Windows).
    }
  }
}
