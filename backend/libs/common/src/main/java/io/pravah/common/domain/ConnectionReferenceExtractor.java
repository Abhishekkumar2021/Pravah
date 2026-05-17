package io.pravah.common.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects named connection references from pipeline definitions. */
public final class ConnectionReferenceExtractor {

  private ConnectionReferenceExtractor() {}

  public static Set<String> extractNamedConnections(Map<String, Object> definition) {
    Set<String> names = new LinkedHashSet<>();
    if (definition == null) {
      return names;
    }
    Object stagesObj = definition.get("stages");
    if (!(stagesObj instanceof List<?> stages)) {
      return names;
    }
    for (Object stageObj : stages) {
      if (!(stageObj instanceof Map<?, ?> stage)) {
        continue;
      }
      Object configObj = stage.get("config");
      if (!(configObj instanceof Map<?, ?> config)) {
        continue;
      }
      Object connectionObj = config.get("connection");
      if (connectionObj instanceof String name && !name.isBlank()) {
        names.add(name.trim());
      }
    }
    return names;
  }
}
