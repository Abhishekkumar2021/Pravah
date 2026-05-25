package io.pravah.runnerservice.infrastructure.grpc;

import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.pravah.common.grpc.GrpcTlsConfig;
import java.io.IOException;

final class RunnerGrpcServerTls {

  private RunnerGrpcServerTls() {}

  static ServerBuilder<?> serverBuilder(int port, GrpcTlsConfig tls) throws IOException {
    if (tls == null || !tls.enabled()) {
      return ServerBuilder.forPort(port);
    }
    tls.validateServer();
    SslContextBuilder sslBuilder =
        GrpcSslContexts.forServer(tls.certChainFile().toFile(), tls.privateKeyFile().toFile());
    if (tls.clientCaFile() != null) {
      // OPTIONAL so RegisterRunner can run with server-trust only before Vault PKI issues a cert.
      // Connect streams require a client cert via RunnerGrpcIdentityInterceptor.
      sslBuilder.trustManager(tls.clientCaFile().toFile()).clientAuth(clientAuthMode(tls));
    }
    return NettyServerBuilder.forPort(port).sslContext(sslBuilder.build());
  }

  static ClientAuth clientAuthMode(GrpcTlsConfig tls) {
    return tls.clientCaFile() != null ? ClientAuth.OPTIONAL : ClientAuth.NONE;
  }
}
