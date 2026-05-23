package io.pravah.runnerservice.infrastructure.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.pravah.common.grpc.GrpcTlsConfig;
import io.pravah.common.grpc.RunnerCertificateIdentity;
import io.pravah.common.grpc.RunnerCertificateIdentityParser;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts runner workload identity from the mTLS client certificate on {@code Connect} streams
 * (ADR-008). When client CA is configured, a parseable certificate identity is required.
 */
@Component
public class RunnerGrpcIdentityInterceptor implements ServerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(RunnerGrpcIdentityInterceptor.class);

  private final RunnerGrpcTlsProperties tlsProperties;

  public RunnerGrpcIdentityInterceptor(RunnerGrpcTlsProperties tlsProperties) {
    this.tlsProperties = tlsProperties;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String method = call.getMethodDescriptor().getFullMethodName();
    boolean isConnect = method != null && method.endsWith("/Connect");

    Optional<RunnerCertificateIdentity> identity = extractIdentity(call);
    if (identity.isPresent()) {
      log.debug(
          "Runner certificate identity: runnerId={}, tenantId={}",
          identity.get().runnerId(),
          identity.get().tenantId().map(Object::toString).orElse("not-in-cert"));
    }

    if (isConnect && isMtlsRequired() && identity.isEmpty()) {
      call.close(
          Status.UNAUTHENTICATED.withDescription(
              "Client certificate with runner SPIFFE identity is required"),
          new Metadata());
      return new ServerCall.Listener<>() {};
    }

    Context context = Context.current();
    if (identity.isPresent()) {
      context = context.withValue(RunnerGrpcIdentityContext.IDENTITY_KEY, identity.get());
    }
    return Contexts.interceptCall(context, call, headers, next);
  }

  private boolean isMtlsRequired() {
    GrpcTlsConfig config = tlsProperties.toConfig();
    return config.enabled() && config.clientCaFile() != null;
  }

  private static Optional<RunnerCertificateIdentity> extractIdentity(ServerCall<?, ?> call) {
    SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
    if (session == null) {
      return Optional.empty();
    }
    try {
      java.security.cert.Certificate[] peerCertificates = session.getPeerCertificates();
      if (peerCertificates.length == 0 || !(peerCertificates[0] instanceof X509Certificate x509)) {
        return Optional.empty();
      }
      return RunnerCertificateIdentityParser.parse(x509);
    } catch (SSLPeerUnverifiedException e) {
      log.debug("No verified peer certificate on gRPC connection", e);
      return Optional.empty();
    }
  }
}
