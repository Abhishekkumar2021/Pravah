package io.pravah.common.grpc;

import java.nio.file.Files;
import java.nio.file.Path;

/** TLS settings for runner gRPC (optional mTLS). Plaintext when {@code enabled} is false. */
public record GrpcTlsConfig(
    boolean enabled,
    Path certChainFile,
    Path privateKeyFile,
    Path clientCaFile,
    Path trustCertFile,
    Path clientCertFile,
    Path clientKeyFile) {

  public static GrpcTlsConfig disabled() {
    return new GrpcTlsConfig(false, null, null, null, null, null, null);
  }

  public static GrpcTlsConfig server(Path certChainFile, Path privateKeyFile, Path clientCaFile) {
    return new GrpcTlsConfig(true, certChainFile, privateKeyFile, clientCaFile, null, null, null);
  }

  public static GrpcTlsConfig client(Path trustCertFile, Path clientCertFile, Path clientKeyFile) {
    return new GrpcTlsConfig(true, null, null, null, trustCertFile, clientCertFile, clientKeyFile);
  }

  public void validateServer() {
    if (!enabled) {
      return;
    }
    requireReadable(certChainFile, "cert chain");
    requireReadable(privateKeyFile, "private key");
    if (clientCaFile != null) {
      requireReadable(clientCaFile, "client CA");
    }
  }

  public void validateClient() {
    if (!enabled) {
      return;
    }
    requireReadable(trustCertFile, "trust certificate");
    if (clientCertFile != null) {
      requireReadable(clientCertFile, "client certificate");
      requireReadable(clientKeyFile, "client private key");
    }
  }

  private static void requireReadable(Path path, String label) {
    if (path == null || !Files.isReadable(path)) {
      throw new IllegalArgumentException("gRPC TLS " + label + " file is missing or unreadable");
    }
  }
}
