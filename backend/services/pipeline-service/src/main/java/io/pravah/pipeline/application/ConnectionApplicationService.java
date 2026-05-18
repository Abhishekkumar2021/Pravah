package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.UserId;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.pipeline.api.dto.ConnectionResponse;
import io.pravah.pipeline.api.dto.CreateConnectionRequest;
import io.pravah.pipeline.api.dto.TestConnectionResponse;
import io.pravah.pipeline.api.dto.UpdateConnectionRequest;
import io.pravah.pipeline.domain.ConnectionType;
import io.pravah.pipeline.domain.PostgresConnectionConfig;
import io.pravah.pipeline.infrastructure.persistence.entity.ConnectionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.ConnectionRepository;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ConnectionApplicationService.class);

  private final ConnectionRepository connectionRepository;
  private final ConnectionCredentialResolver credentialResolver;
  private final ObjectMapper objectMapper;

  public ConnectionApplicationService(
      ConnectionRepository connectionRepository,
      ConnectionCredentialResolver credentialResolver,
      ObjectMapper objectMapper) {
    this.connectionRepository = connectionRepository;
    this.credentialResolver = credentialResolver;
    this.objectMapper = objectMapper;
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
                  tenantId,
                  request.name().trim(),
                  type,
                  objectMapper.valueToTree(config),
                  userId.value(),
                  Instant.now()));
      return toResponse(entity);
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(request.name(), e);
    } catch (JpaSystemException e) {
      if (isUniqueConstraintViolation(e)) {
        throw duplicateName(request.name(), e);
      }
      throw e;
    } catch (PersistenceException e) {
      if (isUniqueConstraintViolation(e)) {
        throw duplicateName(request.name(), e);
      }
      throw e;
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
      entity.updateConfig(objectMapper.valueToTree(newConfig));
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

  /** Opens a short-lived JDBC connection to verify stored settings (US-12.16). */
  @Transactional(readOnly = true)
  public TestConnectionResponse testConnection(UUID connectionId) {
    ResolvedConnection resolved =
        resolveForExecution(findConnectionOrThrow(connectionId).getName());
    Properties props = new Properties();
    props.setProperty("user", resolved.username());
    props.setProperty("password", resolved.password());
    props.setProperty("connectTimeout", "5");
    props.setProperty("loginTimeout", "5");

    try (Connection connection = DriverManager.getConnection(resolved.jdbcUrl(), props)) {
      if (!connection.isValid(5)) {
        return new TestConnectionResponse(false, "Connection failed validation");
      }
      return new TestConnectionResponse(true, "Connection successful");
    } catch (SQLException e) {
      log.warn(
          "Connection test failed",
          kv("connection_id", connectionId),
          kv("connection_name", resolved.name()),
          kv("sql_state", e.getSQLState()),
          e);
      String message =
          e.getMessage() != null && !e.getMessage().isBlank()
              ? e.getMessage()
              : "Connection failed";
      return new TestConnectionResponse(false, message);
    }
  }

  /** Resolves JDBC settings for embedded SQL execution (internal API). */
  @Transactional(readOnly = true)
  public ResolvedConnection resolveForExecution(String name) {
    ConnectionEntity entity = findByNameOrThrow(name);
    Map<String, Object> config = configFromEntity(entity);
    String password = credentialResolver.resolvePassword(config);
    return switch (entity.getType()) {
      case "postgres" ->
          new ResolvedConnection(
              entity.getName(),
              entity.getType(),
              PostgresConnectionConfig.resolveJdbcUrl(config),
              PostgresConnectionConfig.resolveUsername(config),
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

  /**
   * Deep-copies config to plain maps so JSONB round-trips and API responses do not expose Hibernate
   * collection proxies (which Jackson would serialize as {@code {empty, traversableAgain}}).
   */
  private Map<String, Object> copyConfig(Map<String, Object> config) {
    return normalizeConfig(config);
  }

  private Map<String, Object> normalizeConfig(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return Map.of();
    }
    return objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() {});
  }

  private DuplicateConnectionNameException duplicateName(String name, Throwable cause) {
    log.warn(
        "Duplicate connection name",
        kv("tenant_id", TenantContext.getCurrentTenantId()),
        kv("connection_name", name));
    return new DuplicateConnectionNameException(
        "A connection with this name already exists in the tenant", cause);
  }

  private static boolean isUniqueConstraintViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException) {
        return true;
      }
      if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
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

  private Map<String, Object> configFromEntity(ConnectionEntity entity) {
    return objectMapper.convertValue(
        entity.getConfig(), new TypeReference<Map<String, Object>>() {});
  }

  private ConnectionResponse toResponse(ConnectionEntity entity) {
    return new ConnectionResponse(
        entity.getId(),
        entity.getName(),
        entity.getType(),
        configFromEntity(entity),
        entity.getCreatedBy(),
        entity.getCreatedAt());
  }
}
