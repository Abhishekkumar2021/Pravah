package io.pravah.runner;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/** Attaches registration metadata to outbound gRPC calls from the runner agent. */
final class RunnerGrpcClientInterceptor implements ClientInterceptor {

  private final Metadata headers;

  RunnerGrpcClientInterceptor(Metadata headers) {
    this.headers = headers;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata metadata) {
        metadata.merge(headers);
        super.start(responseListener, metadata);
      }
    };
  }
}
