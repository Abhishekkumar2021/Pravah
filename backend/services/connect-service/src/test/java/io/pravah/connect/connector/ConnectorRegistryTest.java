package io.pravah.connect.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectorRegistry")
class ConnectorRegistryTest {

  @Test
  void registersConnectorsAndListsSpecs() {
    Connector postgres =
        mockSourceConnector("postgres", ConnectorType.DATABASE, ConnectorMode.BIDIRECTIONAL);
    Connector kafka = mockSourceConnector("kafka", ConnectorType.STREAMING, ConnectorMode.SOURCE);

    ConnectorRegistry registry = new ConnectorRegistry(List.of(postgres, kafka));

    assertThat(registry.size()).isEqualTo(2);
    assertThat(registry.listSpecs()).hasSize(2);
    assertThat(registry.get("postgres")).isPresent();
    assertThat(registry.listByType(ConnectorType.STREAMING)).hasSize(1);
    assertThat(registry.listSources()).extracting(ConnectorSpec::id).contains("kafka", "postgres");
  }

  @Test
  void searchMatchesNameAndTags() {
    Connector stripe =
        mockConnector("stripe", ConnectorType.SAAS, ConnectorMode.SOURCE, List.of("payments"));
    ConnectorRegistry registry = new ConnectorRegistry(List.of(stripe));

    assertThat(registry.search("payment")).hasSize(1);
    assertThat(registry.search("unknown")).isEmpty();
  }

  private static Connector mockSourceConnector(
      String id, ConnectorType type, ConnectorMode mode, List<String> tags) {
    SourceConnector connector = mock(SourceConnector.class);
    when(connector.getSpec())
        .thenReturn(
            ConnectorSpec.builder(id)
                .name(id)
                .type(type)
                .mode(mode)
                .tags(tags)
                .capabilities(Map.of())
                .configFields(List.of())
                .build());
    return connector;
  }

  private static Connector mockSourceConnector(String id, ConnectorType type, ConnectorMode mode) {
    return mockSourceConnector(id, type, mode, List.of());
  }

  private static Connector mockConnector(
      String id, ConnectorType type, ConnectorMode mode, List<String> tags) {
    Connector connector = mock(Connector.class);
    when(connector.getSpec())
        .thenReturn(
            ConnectorSpec.builder(id)
                .name(id)
                .type(type)
                .mode(mode)
                .tags(tags)
                .capabilities(Map.of())
                .configFields(List.of())
                .build());
    return connector;
  }
}
