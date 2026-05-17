package io.pravah.execution.application.port;

import java.util.UUID;

/** Resolves tenant-scoped named connections for stage execution. */
public interface ConnectionCatalog {

  ResolvedJdbcConnection resolve(UUID tenantId, String connectionName);
}
