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
      sslBuilder.trustManager(tls.clientCaFile().toFile()).clientAuth(ClientAuth.REQUIRE);
    }
    return NettyServerBuilder.forPort(port).sslContext(sslBuilder.build());
  }
}
