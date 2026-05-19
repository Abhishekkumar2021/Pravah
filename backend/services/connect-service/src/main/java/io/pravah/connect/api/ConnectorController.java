package io.pravah.connect.api;

import io.pravah.connect.connector.Connector;
import io.pravah.connect.connector.ConnectorRegistry;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST API for managing connectors. */
@RestController
@RequestMapping("/api/v1/connectors")
@Tag(name = "Connectors", description = "Connector catalog and management")
public class ConnectorController {

  private final ConnectorRegistry registry;

  public ConnectorController(ConnectorRegistry registry) {
    this.registry = registry;
  }

  @Operation(
      summary = "List all connectors",
      description = "Returns catalog of all available connectors")
  @GetMapping
  public ResponseEntity<List<ConnectorSpec>> listConnectors(
      @RequestParam(required = false) ConnectorType type,
      @RequestParam(required = false) String search) {
    List<ConnectorSpec> specs;

    if (search != null && !search.isBlank()) {
      specs = registry.search(search);
    } else if (type != null) {
      specs = registry.listByType(type);
    } else {
      specs = registry.listSpecs();
    }

    return ResponseEntity.ok(specs);
  }

  @Operation(summary = "List source connectors")
  @GetMapping("/sources")
  public ResponseEntity<List<ConnectorSpec>> listSources() {
    return ResponseEntity.ok(registry.listSources());
  }

  @Operation(summary = "List sink connectors")
  @GetMapping("/sinks")
  public ResponseEntity<List<ConnectorSpec>> listSinks() {
    return ResponseEntity.ok(registry.listSinks());
  }

  @Operation(summary = "Get connector specification")
  @GetMapping("/{connectorId}")
  public ResponseEntity<ConnectorSpec> getConnector(@PathVariable String connectorId) {
    return registry
        .get(connectorId)
        .map(Connector::getSpec)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(summary = "Validate connector configuration")
  @PostMapping("/{connectorId}/validate")
  public ResponseEntity<Connector.ValidationResult> validateConfig(
      @PathVariable String connectorId, @RequestBody Map<String, Object> config) {
    return registry
        .get(connectorId)
        .map(connector -> ResponseEntity.ok(connector.validate(config)))
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(summary = "Test connector connection")
  @PostMapping("/{connectorId}/test")
  public ResponseEntity<Connector.TestResult> testConnection(
      @PathVariable String connectorId, @RequestBody Map<String, Object> config) {
    return registry
        .get(connectorId)
        .map(connector -> ResponseEntity.ok(connector.testConnection(config)))
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(summary = "Discover available streams")
  @PostMapping("/{connectorId}/discover")
  public ResponseEntity<List<SourceConnector.StreamInfo>> discoverStreams(
      @PathVariable String connectorId, @RequestBody Map<String, Object> config) {
    return registry
        .getSource(connectorId)
        .map(source -> ResponseEntity.ok(source.discoverStreams(config)))
        .orElse(ResponseEntity.notFound().build());
  }
}
