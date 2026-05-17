package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates Spark job stage configurations (US-01.03). */
public final class SparkStageValidator {

  private SparkStageValidator() {}

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
      if (stageType != null && "spark".equalsIgnoreCase(stageType)) {
        validateSparkStage(stage, stageId, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid pipeline definition: " + String.join("; ", errors));
    }
  }

  private static void validateSparkStage(Map<?, ?> stage, String stageId, List<String> errors) {
    Object configObj = stage.get("config");
    if (!(configObj instanceof Map<?, ?> config)) {
      errors.add("Spark stage '%s' is missing required 'config' section".formatted(stageId));
      return;
    }
    Object mainClass = config.get("main_class");
    if (mainClass == null) {
      mainClass = config.get("mainClass");
    }
    Object applicationJar = config.get("application_jar");
    if (applicationJar == null) {
      applicationJar = config.get("applicationJar");
    }
    boolean hasMain = mainClass != null && !mainClass.toString().isBlank();
    boolean hasJar = applicationJar != null && !applicationJar.toString().isBlank();
    if (!hasMain && !hasJar) {
      errors.add(
          "Spark stage '%s' requires config.main_class or config.application_jar"
              .formatted(stageId));
    }
  }

  private static String getString(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }
}
