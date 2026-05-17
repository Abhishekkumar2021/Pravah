package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.UserId;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.pipeline.api.dto.ConnectionResponse;
import io.pravah.pipeline.api.dto.CreateConnectionRequest;
import io.pravah.pipeline.api.dto.UpdateConnectionRequest;
import io.pravah.pipeline.domain.ConnectionType;
import io.pravah.pipeline.domain.PostgresConnectionConfig;
import io.pravah.pipeline.infrastructure.persistence.entity.ConnectionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.ConnectionRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ConnectionApplicationService.class);

  private final ConnectionRepository connectionRepository;
  private final ConnectionCredentialResolver credentialResolver;

  public ConnectionApplicationService(
      ConnectionRepository connectionRepository, ConnectionCredentialResolver credentialResolver) {
    this.connectionRepository = connectionRepository;
    this.credentialResolver = credentialResolver;
  }

  @Transactional
  public ConnectionResponse createConnection(CreateConnectionRequest request) {
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();
    String type = ConnectionType.parse(request.type());
    Map<String, Object> config = copyConfig(request.config());
    validateConfig(type, config);
    credentialResolver.validateCredentials(config);

    log.info(
        "Creating connection",
        kv("tenant_id", tenantId),
        kv("connection_name", request.name()),
        kv("type", type));

    try {
      ConnectionEntity entity =
          connectionRepository.save(
              new ConnectionEntity(
                  tenantId, request.name().trim(), type, config, userId.value(), Instant.now()));
      return toResponse(entity);
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(request.name(), e);
    }
  }

  @Transactional(readOnly = true)
  public List<ConnectionResponse> listConnections() {
    requireTenantId();
    return connectionRepository.findByTenantIdOrderByNameAsc(requireTenantId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ConnectionResponse getConnection(UUID connectionId) {
    return toResponse(findConnectionOrThrow(connectionId));
  }

  @Transactional(readOnly = true)
  public ConnectionResponse getConnectionByName(String name) {
    return toResponse(findByNameOrThrow(name));
  }

  @Transactional
  public ConnectionResponse updateConnection(UUID connectionId, UpdateConnectionRequest request) {
    requireTenantId();
    requireUserId();

    ConnectionEntity entity = findConnectionOrThrow(connectionId);
    if (request.config() != null) {
      Map<String, Object> newConfig = copyConfig(request.config());
      validateConfig(entity.getType(), newConfig);
      credentialResolver.validateCredentials(newConfig);
      entity.updateConfig(newConfig);
    }
    return toResponse(entity);
  }

  @Transactional
  public void deleteConnection(UUID connectionId) {
    requireTenantId();
    ConnectionEntity entity = findConnectionOrThrow(connectionId);
    connectionRepository.delete(entity);
    log.info("Deleted connection", kv("connection_id", connectionId), kv("name", entity.getName()));
  }

  /** Resolves JDBC settings for embedded SQL execution (internal API). */
  @Transactional(readOnly = true)
  public ResolvedConnection resolveForExecution(String name) {
    ConnectionEntity entity = findByNameOrThrow(name);
    String password = credentialResolver.resolvePassword(entity.getConfig());
    return switch (entity.getType()) {
      case "postgres" ->
          new ResolvedConnection(
              entity.getName(),
              entity.getType(),
              PostgresConnectionConfig.resolveJdbcUrl(entity.getConfig()),
              PostgresConnectionConfig.resolveUsername(entity.getConfig()),
              password);
      default ->
          throw new IllegalStateException("Unsupported connection type: " + entity.getType());
    };
  }

  /** Verifies all named connection references exist for the current tenant. */
  @Transactional(readOnly = true)
  public void validateConnectionReferences(Iterable<String> connectionNames) {
    UUID tenantId = requireTenantId();
    for (String name : connectionNames) {
      if (name == null || name.isBlank()) {
        continue;
      }
      if (!connectionRepository.existsByTenantIdAndName(tenantId, name)) {
        throw new IllegalArgumentException("Unknown connection: '%s'".formatted(name));
      }
    }
  }

  private ConnectionEntity findConnectionOrThrow(UUID connectionId) {
    UUID tenantId = requireTenantId();
    return connectionRepository
        .findById(connectionId)
        .filter(c -> c.getTenantId().equals(tenantId))
        .orElseThrow(() -> new EntityNotFoundException("Connection", connectionId));
  }

  private ConnectionEntity findByNameOrThrow(String name) {
    UUID tenantId = requireTenantId();
    return connectionRepository
        .findByTenantIdAndName(tenantId, name)
        .orElseThrow(() -> new EntityNotFoundException("Connection", name));
  }

  private static void validateConfig(String type, Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      throw new IllegalArgumentException("Connection config is required");
    }
    if ("postgres".equals(type)) {
      PostgresConnectionConfig.validate(config);
    }
  }

  private static Map<String, Object> copyConfig(Map<String, Object> config) {
    return Map.copyOf(config);
  }

  private DuplicateConnectionNameException duplicateName(String name, Throwable cause) {
    return new DuplicateConnectionNameException(
        "A connection with this name already exists in the tenant", cause);
  }

  private static UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  private static UserId requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("Missing user context");
    }
    return UserId.of(userId);
  }

  private ConnectionResponse toResponse(ConnectionEntity entity) {
    return new ConnectionResponse(
        entity.getId(),
        entity.getName(),
        entity.getType(),
        entity.getConfig(),
        entity.getCreatedBy(),
        entity.getCreatedAt());
  }
}
