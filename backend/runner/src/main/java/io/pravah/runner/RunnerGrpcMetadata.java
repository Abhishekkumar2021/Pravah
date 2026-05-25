package io.pravah.runner;

import io.grpc.Metadata;
import java.util.UUID;

/** Client-side gRPC metadata for runner-service registration. */
final class RunnerGrpcMetadata {

  static final String TENANT_KEY = "x-pravah-tenant-id";
  static final String BOOTSTRAP_SECRET_KEY = "x-pravah-runner-bootstrap-secret";

  private RunnerGrpcMetadata() {}

  static Metadata registrationHeaders(UUID tenantId, String bootstrapSecret) {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of(TENANT_KEY, Metadata.ASCII_STRING_MARSHALLER), tenantId.toString());
    metadata.put(
        Metadata.Key.of(BOOTSTRAP_SECRET_KEY, Metadata.ASCII_STRING_MARSHALLER), bootstrapSecret);
    return metadata;
  }
}
