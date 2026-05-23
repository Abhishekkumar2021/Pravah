package io.pravah.runnerservice.infrastructure.grpc;

import io.grpc.Context;
import io.pravah.common.grpc.RunnerCertificateIdentity;
import java.util.Optional;

/** gRPC {@link Context} accessor for runner mTLS certificate identity (ADR-008). */
public final class RunnerGrpcIdentityContext {

  static final Context.Key<RunnerCertificateIdentity> IDENTITY_KEY =
      Context.key("runner-certificate-identity");

  private RunnerGrpcIdentityContext() {}

  public static Optional<RunnerCertificateIdentity> current() {
    RunnerCertificateIdentity identity = IDENTITY_KEY.get();
    return identity == null ? Optional.empty() : Optional.of(identity);
  }
}
