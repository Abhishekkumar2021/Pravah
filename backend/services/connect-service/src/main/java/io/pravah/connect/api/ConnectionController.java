package io.pravah.connect.api;

import io.pravah.connect.connector.Connector;
import io.pravah.connect.domain.Connection;
import io.pravah.connect.service.ConnectionService;
import io.pravah.spring.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST API for managing configured connections. */
@RestController
@RequestMapping("/api/v1/connections")
@Tag(name = "Connections", description = "Connection management")
public class ConnectionController {

  private final ConnectionService connectionService;

  public ConnectionController(ConnectionService connectionService) {
    this.connectionService = connectionService;
  }

  private UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context not set");
    }
    return tenantId;
  }

  @Operation(summary = "List all connections")
  @GetMapping
  public ResponseEntity<List<ConnectionDto>> listConnections(
      @RequestParam(required = false) String connectorId,
      @RequestParam(required = false) String search) {
    UUID tenantId = requireTenantId();
    List<Connection> connections;

    if (search != null && !search.isBlank()) {
      connections = connectionService.search(tenantId, search);
    } else if (connectorId != null && !connectorId.isBlank()) {
      connections = connectionService.listByConnector(tenantId, connectorId);
    } else {
      connections = connectionService.list(tenantId);
    }

    return ResponseEntity.ok(connections.stream().map(this::toDto).toList());
  }

  @Operation(summary = "Get connection by ID")
  @GetMapping("/{connectionId}")
  public ResponseEntity<ConnectionDto> getConnection(@PathVariable UUID connectionId) {
    UUID tenantId = requireTenantId();
    return connectionService
        .get(tenantId, connectionId)
        .map(this::toDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(summary = "Create a new connection")
  @PostMapping
  public ResponseEntity<ConnectionDto> createConnection(
      @RequestBody CreateConnectionRequest request) {
    UUID tenantId = requireTenantId();
    Connection connection =
        connectionService.create(
            tenantId,
            new ConnectionService.CreateConnectionRequest(
                request.name(), request.description(), request.connectorId(), request.config()));
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(connection));
  }

  @Operation(summary = "Update a connection")
  @PutMapping("/{connectionId}")
  public ResponseEntity<ConnectionDto> updateConnection(
      @PathVariable UUID connectionId, @RequestBody UpdateConnectionRequest request) {
    UUID tenantId = requireTenantId();
    Connection connection =
        connectionService.update(
            tenantId,
            connectionId,
            new ConnectionService.UpdateConnectionRequest(
                request.name(), request.description(), request.config()));
    return ResponseEntity.ok(toDto(connection));
  }

  @Operation(summary = "Delete a connection")
  @DeleteMapping("/{connectionId}")
  public ResponseEntity<Void> deleteConnection(@PathVariable UUID connectionId) {
    UUID tenantId = requireTenantId();
    connectionService.delete(tenantId, connectionId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Test a connection")
  @PostMapping("/{connectionId}/test")
  public ResponseEntity<Connector.TestResult> testConnection(@PathVariable UUID connectionId) {
    UUID tenantId = requireTenantId();
    return ResponseEntity.ok(connectionService.testConnection(tenantId, connectionId));
  }

  @Operation(summary = "Test connection configuration without saving")
  @PostMapping("/test")
  public ResponseEntity<Connector.TestResult> testConfig(@RequestBody TestConfigRequest request) {
    return ResponseEntity.ok(connectionService.testConfig(request.connectorId(), request.config()));
  }

  private ConnectionDto toDto(Connection connection) {
    return new ConnectionDto(
        connection.getId(),
        connection.getName(),
        connection.getDescription(),
        connection.getConnectorId(),
        connection.getStatus().name(),
        connection.getLastTestedAt(),
        connection.getLastTestSuccess(),
        connection.getLastTestMessage(),
        connection.getCreatedAt(),
        connection.getUpdatedAt());
  }

  // Request/Response DTOs

  public record CreateConnectionRequest(
      String name, String description, String connectorId, Map<String, Object> config) {}

  public record UpdateConnectionRequest(
      String name, String description, Map<String, Object> config) {}

  public record TestConfigRequest(String connectorId, Map<String, Object> config) {}

  public record ConnectionDto(
      UUID id,
      String name,
      String description,
      String connectorId,
      String status,
      java.time.Instant lastTestedAt,
      Boolean lastTestSuccess,
      String lastTestMessage,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}
}
