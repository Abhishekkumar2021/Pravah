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
    validateRequirements(config, stageId, errors);
    validatePythonVersion(config, stageId, errors);
  }

  private static void validateRequirements(Map<?, ?> config, String stageId, List<String> errors) {
    Object requirements = config.get("requirements");
    Object requirementsFile = config.get("requirements_file");
    if (requirements == null && requirementsFile == null) {
      return;
    }
    if (requirements != null && !(requirements instanceof List<?>)) {
      errors.add(
          "Python stage '%s' config.requirements must be a list of package specifiers"
              .formatted(stageId));
    }
    if (requirements instanceof List<?> list) {
      for (Object item : list) {
        if (item == null || item.toString().isBlank()) {
          errors.add(
              "Python stage '%s' config.requirements must not contain blank entries"
                  .formatted(stageId));
          break;
        }
      }
    }
    if (requirementsFile != null && requirementsFile.toString().isBlank()) {
      errors.add("Python stage '%s' config.requirements_file must not be blank".formatted(stageId));
    }
  }

  private static void validatePythonVersion(Map<?, ?> config, String stageId, List<String> errors) {
    Object version = config.get("python_version");
    if (version == null) {
      return;
    }
    String raw = version.toString().trim();
    if (raw.isEmpty()) {
      return;
    }
    try {
      double majorMinor = parsePythonVersion(raw);
      if (majorMinor < 3.9) {
        errors.add(
            "Python stage '%s' config.python_version must be 3.9 or higher (got %s)"
                .formatted(stageId, raw));
      }
    } catch (IllegalArgumentException e) {
      errors.add("Python stage '%s' config.python_version is invalid: %s".formatted(stageId, raw));
    }
  }

  /** Returns {@code major.minor} as a double (e.g. 3.12). */
  static double parsePythonVersion(String raw) {
    String normalized = raw.startsWith("python") ? raw.substring("python".length()) : raw;
    normalized = normalized.trim();
    if (normalized.startsWith("v")) {
      normalized = normalized.substring(1);
    }
    String[] parts = normalized.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("expected major.minor");
    }
    return Double.parseDouble(parts[0] + "." + parts[1]);
  }

  private static String getString(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }
}
