package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectionReferenceExtractorTest {

  @Test
  void extractNamedConnections_collectsSqlStageReferences() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1", "connection", "warehouse")),
                Map.of(
                    "id", "load",
                    "type", "sql",
                    "config",
                        Map.of(
                            "query",
                            "SELECT 2",
                            "connection",
                            Map.of("url", "jdbc:postgresql://localhost/db")))));

    Set<String> names = ConnectionReferenceExtractor.extractNamedConnections(definition);

    assertThat(names).containsExactly("warehouse");
  }
}
