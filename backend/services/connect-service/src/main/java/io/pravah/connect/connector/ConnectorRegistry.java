package io.pravah.connect.connector;

import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for all available connectors. Connectors self-register via Spring's dependency
 * injection.
 */
@Component
public class ConnectorRegistry {

  private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

  private final Map<String, Connector> connectors = new ConcurrentHashMap<>();

  public ConnectorRegistry(List<Connector> connectorBeans) {
    for (Connector connector : connectorBeans) {
      String id = connector.getSpec().id();
      if (connectors.containsKey(id)) {
        log.warn("Connector '{}' already registered, overwriting", id);
      }
      connectors.put(id, connector);
    }
    log.info(
        "Registered {} connectors: {}",
        connectors.size(),
        connectors.keySet().stream().sorted().collect(Collectors.joining(", ")));
  }

  /** Registers a connector. */
  public void register(Connector connector) {
    String id = connector.getSpec().id();
    if (connectors.containsKey(id)) {
      log.warn("Connector '{}' already registered, overwriting", id);
    }
    connectors.put(id, connector);
  }

  /** Gets a connector by ID. */
  public Optional<Connector> get(String id) {
    return Optional.ofNullable(connectors.get(id));
  }

  /** Gets a source connector by ID. */
  public Optional<SourceConnector> getSource(String id) {
    return get(id).filter(c -> c instanceof SourceConnector).map(c -> (SourceConnector) c);
  }

  /** Gets a sink connector by ID. */
  public Optional<SinkConnector> getSink(String id) {
    return get(id).filter(c -> c instanceof SinkConnector).map(c -> (SinkConnector) c);
  }

  /** Lists all connector specifications. */
  public List<ConnectorSpec> listSpecs() {
    return connectors.values().stream()
        .map(Connector::getSpec)
        .sorted(Comparator.comparing(ConnectorSpec::name))
        .toList();
  }

  /** Lists connectors by type. */
  public List<ConnectorSpec> listByType(ConnectorType type) {
    return connectors.values().stream()
        .map(Connector::getSpec)
        .filter(s -> s.type() == type)
        .sorted(Comparator.comparing(ConnectorSpec::name))
        .toList();
  }

  /** Lists source connectors. */
  public List<ConnectorSpec> listSources() {
    return connectors.values().stream()
        .filter(c -> c instanceof SourceConnector)
        .map(Connector::getSpec)
        .sorted(Comparator.comparing(ConnectorSpec::name))
        .toList();
  }

  /** Lists sink connectors. */
  public List<ConnectorSpec> listSinks() {
    return connectors.values().stream()
        .filter(c -> c instanceof SinkConnector)
        .map(Connector::getSpec)
        .sorted(Comparator.comparing(ConnectorSpec::name))
        .toList();
  }

  /** Searches connectors by name or tags. */
  public List<ConnectorSpec> search(String query) {
    String lowerQuery = query.toLowerCase();
    return connectors.values().stream()
        .map(Connector::getSpec)
        .filter(
            s ->
                s.name().toLowerCase().contains(lowerQuery)
                    || s.description().toLowerCase().contains(lowerQuery)
                    || s.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
        .sorted(Comparator.comparing(ConnectorSpec::name))
        .toList();
  }

  /** Returns the total number of registered connectors. */
  public int size() {
    return connectors.size();
  }
}
