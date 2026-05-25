package io.pravah.runnerservice.infrastructure.grpc;

/**
 * gRPC metadata keys for runner agent registration and stream authentication.
 *
 * <p>Registration uses tenant id + bootstrap secret; the bidirectional stream is authenticated with
 * the issued runner token on each heartbeat (see ADR-005).
 */
public final class RunnerGrpcAuth {

  public static final String TENANT_METADATA_KEY = "x-pravah-tenant-id";
  public static final String BOOTSTRAP_SECRET_METADATA_KEY = "x-pravah-runner-bootstrap-secret";

  private RunnerGrpcAuth() {}
}
