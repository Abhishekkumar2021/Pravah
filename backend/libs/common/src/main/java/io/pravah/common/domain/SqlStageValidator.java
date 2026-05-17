package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates SQL stage configurations in pipeline definitions (US-02.14).
 *
 * <p>Ensures that stages with {@code type: sql} have the required {@code query} field and valid
 * connection configuration (if provided).
 *
 * <p>Validation is performed during pipeline publish to catch configuration errors early.
 */
public final class SqlStageValidator {

  private static final int MAX_QUERY_LENGTH = 1_000_000;

  private SqlStageValidator() {}

  /**
   * Validates SQL stage configurations in the pipeline definition.
   *
   * @param definition the pipeline definition map
   * @throws IllegalArgumentException if validation fails
   */
  public static void validateDefinition(Map<String, Object> definition) {
    if (definition == null) {
      return;
    }

    Object stagesObj = definition.get("stages");
    if (!(stagesObj instanceof List<?> stages)) {
      return;
    }

    List<String> errors = new ArrayList<>();
    Set<String> stageIds = new HashSet<>();

    for (int i = 0; i < stages.size(); i++) {
      Object stageObj = stages.get(i);
      if (!(stageObj instanceof Map<?, ?> stage)) {
        continue;
      }

      String stageId = getString(stage, "id");
      if (stageId == null || stageId.isBlank()) {
        errors.add("Stage at index %d is missing required 'id' field".formatted(i));
        continue;
      }

      if (!stageIds.add(stageId)) {
        errors.add("Duplicate stage id: '%s'".formatted(stageId));
      }

      String stageType = getString(stage, "type");
      if (stageType == null) {
        continue;
      }

      if ("sql".equalsIgnoreCase(stageType)) {
        validateSqlStage(stage, stageId, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid pipeline definition: " + String.join("; ", errors));
    }
  }

  private static void validateSqlStage(Map<?, ?> stage, String stageId, List<String> errors) {
    Object configObj = stage.get("config");
    if (!(configObj instanceof Map<?, ?> config)) {
      errors.add("SQL stage '%s' is missing required 'config' section".formatted(stageId));
      return;
    }

    String query = getString(config, "query");
    if (query == null || query.isBlank()) {
      errors.add("SQL stage '%s' is missing required 'query' in config".formatted(stageId));
      return;
    }

    if (query.length() > MAX_QUERY_LENGTH) {
      errors.add(
          "SQL stage '%s' query exceeds maximum length of %d characters"
              .formatted(stageId, MAX_QUERY_LENGTH));
      return;
    }

    Object connectionObj = config.get("connection");
    if (connectionObj != null) {
      if (connectionObj instanceof String name) {
        if (name.isBlank()) {
          errors.add("SQL stage '%s' connection name must not be blank".formatted(stageId));
        }
      } else if (connectionObj instanceof Map<?, ?> connection) {
        validateInlineConnection(connection, stageId, errors);
      } else {
        errors.add(
            "SQL stage '%s' connection must be a name (string) or inline mapping"
                .formatted(stageId));
      }
    }
  }

  private static void validateInlineConnection(
      Map<?, ?> connection, String stageId, List<String> errors) {
    String url = getString(connection, "url");
    if (url == null || url.isBlank()) {
      errors.add("SQL stage '%s' connection is missing required 'url'".formatted(stageId));
    } else if (!url.startsWith("jdbc:")) {
      errors.add("SQL stage '%s' connection url must start with 'jdbc:'".formatted(stageId));
    }
  }

  private static String getString(Map<?, ?> map, String key) {
    Object v = map.get(key);
    return v != null ? v.toString() : null;
  }
}
