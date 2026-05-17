package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates Python script stage configurations (US-01.03). */
public final class PythonStageValidator {

  private static final int MAX_SCRIPT_LENGTH = 512_000;

  private PythonStageValidator() {}

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
      if (stageType != null && "python".equalsIgnoreCase(stageType)) {
        validatePythonStage(stage, stageId, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid pipeline definition: " + String.join("; ", errors));
    }
  }

  private static void validatePythonStage(Map<?, ?> stage, String stageId, List<String> errors) {
    Object configObj = stage.get("config");
    if (!(configObj instanceof Map<?, ?> config)) {
      errors.add("Python stage '%s' is missing required 'config' section".formatted(stageId));
      return;
    }
    Object script = config.get("script");
    Object command = config.get("command");
    boolean hasScript = script != null && !script.toString().isBlank();
    boolean hasCommand = command instanceof List<?> list && !list.isEmpty();
    if (!hasScript && !hasCommand) {
      errors.add("Python stage '%s' requires config.script or config.command".formatted(stageId));
      return;
    }
    if (hasScript && script.toString().length() > MAX_SCRIPT_LENGTH) {
      errors.add(
          "Python stage '%s' config.script exceeds maximum length (%d)"
              .formatted(stageId, MAX_SCRIPT_LENGTH));
    }
  }

  private static String getString(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }
}
