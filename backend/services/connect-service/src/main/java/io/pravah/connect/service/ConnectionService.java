package io.pravah.connect.service;

import io.pravah.connect.connector.Connector;
import io.pravah.connect.connector.ConnectorRegistry;
import io.pravah.connect.domain.Connection;
import io.pravah.connect.domain.ConnectionStatus;
import io.pravah.connect.repository.ConnectionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing connections. */
@Service
@Transactional
public class ConnectionService {

  private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

  private final ConnectionRepository repository;
  private final ConnectorRegistry connectorRegistry;

  public ConnectionService(ConnectionRepository repository, ConnectorRegistry connectorRegistry) {
    this.repository = repository;
    this.connectorRegistry = connectorRegistry;
  }

  /** Creates a new connection. */
  public Connection create(UUID tenantId, CreateConnectionRequest request) {
    // Validate connector exists
    Connector connector =
        connectorRegistry
            .get(request.connectorId())
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown connector: " + request.connectorId()));

    // Validate config
    var validation = connector.validate(request.config());
    if (!validation.valid()) {
      throw new IllegalArgumentException("Invalid configuration: " + validation.errors());
    }

    // Check for duplicate name
    if (repository.existsByTenantIdAndName(tenantId, request.name())) {
      throw new IllegalArgumentException(
          "Connection with name '" + request.name() + "' already exists");
    }

    Connection connection = new Connection();
    connection.setTenantId(tenantId);
    connection.setName(request.name());
    connection.setDescription(request.description());
    connection.setConnectorId(request.connectorId());
    connection.setConfig(request.config());
    connection.setStatus(ConnectionStatus.INACTIVE);

    return repository.save(connection);
  }

  /** Updates an existing connection. */
  public Connection update(UUID tenantId, UUID connectionId, UpdateConnectionRequest request) {
    Connection connection =
        repository
            .findByIdAndTenantId(connectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

    if (request.name() != null && !request.name().equals(connection.getName())) {
      if (repository.existsByTenantIdAndName(tenantId, request.name())) {
        throw new IllegalArgumentException(
            "Connection with name '" + request.name() + "' already exists");
      }
      connection.setName(request.name());
    }

    if (request.description() != null) {
      connection.setDescription(request.description());
    }

    if (request.config() != null) {
      Connector connector =
          connectorRegistry
              .get(connection.getConnectorId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Unknown connector: " + connection.getConnectorId()));

      var validation = connector.validate(request.config());
      if (!validation.valid()) {
        throw new IllegalArgumentException("Invalid configuration: " + validation.errors());
      }

      connection.setConfig(request.config());
    }

    return repository.save(connection);
  }

  /** Tests a connection. */
  public Connector.TestResult testConnection(UUID tenantId, UUID connectionId) {
    Connection connection =
        repository
            .findByIdAndTenantId(connectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

    Connector connector =
        connectorRegistry
            .get(connection.getConnectorId())
            .orElseThrow(
                () ->
                    new IllegalStateException("Unknown connector: " + connection.getConnectorId()));

    connection.setStatus(ConnectionStatus.TESTING);
    connection.setLastTestedAt(Instant.now());
    repository.save(connection);

    var result = connector.testConnection(connection.getConfig());

    connection.setLastTestSuccess(result.success());
    connection.setLastTestMessage(result.message());
    connection.setStatus(result.success() ? ConnectionStatus.ACTIVE : ConnectionStatus.FAILED);
    repository.save(connection);

    log.info(
        "Connection test completed: connectionId={}, success={}, message={}",
        connectionId,
        result.success(),
        result.message());

    return result;
  }

  /** Tests a connection configuration without saving. */
  public Connector.TestResult testConfig(String connectorId, Map<String, Object> config) {
    Connector connector =
        connectorRegistry
            .get(connectorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));

    var validation = connector.validate(config);
    if (!validation.valid()) {
      return Connector.TestResult.failure("Invalid configuration: " + validation.errors());
    }

    return connector.testConnection(config);
  }

  /** Gets a connection by ID. */
  @Transactional(readOnly = true)
  public Optional<Connection> get(UUID tenantId, UUID connectionId) {
    return repository.findByIdAndTenantId(connectionId, tenantId);
  }

  /** Lists all connections for a tenant. */
  @Transactional(readOnly = true)
  public List<Connection> list(UUID tenantId) {
    return repository.findByTenantId(tenantId);
  }

  /** Lists connections by connector type. */
  @Transactional(readOnly = true)
  public List<Connection> listByConnector(UUID tenantId, String connectorId) {
    return repository.findByTenantIdAndConnectorId(tenantId, connectorId);
  }

  /** Searches connections. */
  @Transactional(readOnly = true)
  public List<Connection> search(UUID tenantId, String query) {
    return repository.searchByTenantId(tenantId, query);
  }

  /** Deletes a connection. */
  public void delete(UUID tenantId, UUID connectionId) {
    Connection connection =
        repository
            .findByIdAndTenantId(connectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

    repository.delete(connection);
    log.info("Connection deleted: connectionId={}, name={}", connectionId, connection.getName());
  }

  // Request records

  public record CreateConnectionRequest(
      String name, String description, String connectorId, Map<String, Object> config) {}

  public record UpdateConnectionRequest(
      String name, String description, Map<String, Object> config) {}
}
