package io.pravah.runnerservice.infrastructure.grpc;

/** gRPC metadata keys for runner agent authentication (alpha: shared bootstrap secret + tenant). */
public final class RunnerGrpcAuth {

  public static final String TENANT_METADATA_KEY = "x-pravah-tenant-id";
  public static final String BOOTSTRAP_SECRET_METADATA_KEY = "x-pravah-runner-bootstrap-secret";

  private RunnerGrpcAuth() {}
}
