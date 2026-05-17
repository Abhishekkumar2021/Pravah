package io.pravah.execution.application.port;

import java.time.Instant;
import java.util.UUID;

/** Resolves tenant secret values from pipeline-service at stage execution time. */
public interface SecretCatalog {

  String resolve(UUID tenantId, UUID executionId, Instant executionTime, String secretName);
}
