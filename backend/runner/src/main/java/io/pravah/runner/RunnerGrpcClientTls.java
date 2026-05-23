package io.pravah.runner;

import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.pravah.common.grpc.GrpcTlsConfig;
import java.io.IOException;

final class RunnerGrpcClientTls {

  private RunnerGrpcClientTls() {}

  @SuppressWarnings("unchecked")
  static ManagedChannelBuilder<?> channelBuilder(String host, int port, GrpcTlsConfig tls)
      throws IOException {
    if (tls == null || !tls.enabled()) {
      return ManagedChannelBuilder.forAddress(host, port).usePlaintext();
    }
    tls.validateClient();
    SslContextBuilder sslBuilder =
        GrpcSslContexts.forClient().trustManager(tls.trustCertFile().toFile());
    if (tls.clientCertFile() != null && tls.clientKeyFile() != null) {
      sslBuilder.keyManager(tls.clientCertFile().toFile(), tls.clientKeyFile().toFile());
    }
    return NettyChannelBuilder.forAddress(host, port).sslContext(sslBuilder.build());
  }
}
