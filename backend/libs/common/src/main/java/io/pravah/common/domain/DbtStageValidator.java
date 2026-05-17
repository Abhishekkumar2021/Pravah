package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates dbt model stage configurations (US-01.03). */
public final class DbtStageValidator {

  private DbtStageValidator() {}

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
      if (stageType != null && "dbt".equalsIgnoreCase(stageType)) {
        validateDbtStage(stage, stageId, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid pipeline definition: " + String.join("; ", errors));
    }
  }

  private static void validateDbtStage(Map<?, ?> stage, String stageId, List<String> errors) {
    Object configObj = stage.get("config");
    if (!(configObj instanceof Map<?, ?> config)) {
      errors.add("dbt stage '%s' is missing required 'config' section".formatted(stageId));
      return;
    }
    Object models = config.get("models");
    Object select = config.get("select");
    boolean hasModels = models instanceof List<?> list && !list.isEmpty();
    boolean hasSelect = select != null && !select.toString().isBlank();
    if (!hasModels && !hasSelect) {
      errors.add(
          "dbt stage '%s' requires config.models (list) or config.select".formatted(stageId));
    }
  }

  private static String getString(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }
}
