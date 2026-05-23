package io.pravah.common.grpc;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Workload identity extracted from a runner agent mTLS client certificate (ADR-008). */
public record RunnerCertificateIdentity(UUID runnerId, Optional<UUID> tenantId) {

  public RunnerCertificateIdentity {
    Objects.requireNonNull(runnerId, "runnerId");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
