package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates container stage configurations in pipeline definitions (US-02.17).
 *
 * <p>Ensures that stages with {@code type: container} declare a non-blank {@code image} and valid
 * optional {@code command}, {@code env}, and {@code resources} blocks. Rejects unsafe configuration
 * keys that are not supported in the embedded Docker executor (volumes, privileged mode, etc.).
 */
public final class ContainerStageValidator {

  private static final int MAX_IMAGE_LENGTH = 512;
  private static final int MAX_COMMAND_ARGS = 256;
  private static final int MAX_ENV_VARS = 128;

  /**
   * Docker image reference: registry/path:tag or digest — no shell metacharacters.
   *
   * <p>ReDoS-safe: MAX_IMAGE_LENGTH (512) limits input; possessive quantifiers prevent
   * backtracking.
   */
  private static final Pattern SAFE_IMAGE =
      Pattern.compile(
          "^[a-zA-Z0-9][a-zA-Z0-9._\\-/]*+(?::[a-zA-Z0-9._\\-]++)?(?:@sha256:[a-f0-9]{64})?$");

  private static final Set<String> DISALLOWED_CONFIG_KEYS =
      Set.of(
          "privileged",
          "volumes",
          "volume_mounts",
          "mounts",
          "network_mode",
          "host_network",
          "cap_add",
          "cap_drop",
          "user",
          "pid_mode",
          "ipc_mode");

  private ContainerStageValidator() {}

  /**
   * Validates container stage configurations in the pipeline definition.
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

      if ("container".equalsIgnoreCase(stageType)) {
        validateContainerStage(stage, stageId, errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid pipeline definition: " + String.join("; ", errors));
    }
  }

  private static void validateContainerStage(Map<?, ?> stage, String stageId, List<String> errors) {
    Object configObj = stage.get("config");
    if (!(configObj instanceof Map<?, ?> config)) {
      errors.add("Container stage '%s' is missing required 'config' section".formatted(stageId));
      return;
    }

    for (Object key : config.keySet()) {
      if (key != null && DISALLOWED_CONFIG_KEYS.contains(key.toString())) {
        errors.add(
            "Container stage '%s' does not support config key '%s'"
                .formatted(stageId, key.toString()));
      }
    }

    String image = getString(config, "image");
    if (image == null || image.isBlank()) {
      errors.add("Container stage '%s' is missing required 'image' in config".formatted(stageId));
    } else {
      validateImage(image, stageId, errors);
    }

    validateCommand(config.get("command"), stageId, errors);

    Object envObj = config.get("env");
    if (envObj != null) {
      if (!(envObj instanceof Map<?, ?> env)) {
        errors.add(
            "Container stage '%s' env must be a mapping of name to value".formatted(stageId));
      } else {
        validateEnv(env, stageId, errors);
      }
    }

    Object resourcesObj = config.get("resources");
    if (resourcesObj != null) {
      if (!(resourcesObj instanceof Map<?, ?> resources)) {
        errors.add("Container stage '%s' resources must be a mapping".formatted(stageId));
      } else {
        validateResources(resources, stageId, errors);
      }
    }
  }

  private static void validateImage(String image, String stageId, List<String> errors) {
    String trimmed = image.trim();
    if (trimmed.length() > MAX_IMAGE_LENGTH) {
      errors.add(
          "Container stage '%s' image exceeds maximum length of %d characters"
              .formatted(stageId, MAX_IMAGE_LENGTH));
      return;
    }
    if (trimmed.contains("\n") || trimmed.contains("\r")) {
      errors.add("Container stage '%s' image must not contain newlines".formatted(stageId));
      return;
    }
    if (!SAFE_IMAGE.matcher(trimmed).matches()) {
      errors.add(
          "Container stage '%s' image has invalid format (use name:tag or name@sha256:digest)"
              .formatted(stageId));
    }
  }

  private static void validateCommand(Object commandObj, String stageId, List<String> errors) {
    if (commandObj == null) {
      return;
    }
    if (commandObj instanceof String cmd) {
      if (cmd.isBlank()) {
        errors.add("Container stage '%s' command must not be blank".formatted(stageId));
      }
      return;
    }
    if (commandObj instanceof List<?> command) {
      if (command.isEmpty()) {
        errors.add("Container stage '%s' command list must not be empty".formatted(stageId));
      } else if (command.size() > MAX_COMMAND_ARGS) {
        errors.add(
            "Container stage '%s' command exceeds maximum of %d arguments"
                .formatted(stageId, MAX_COMMAND_ARGS));
      } else {
        for (int i = 0; i < command.size(); i++) {
          Object arg = command.get(i);
          if (arg == null || arg.toString().isBlank()) {
            errors.add(
                "Container stage '%s' command argument at index %d must not be blank"
                    .formatted(stageId, i));
          }
        }
      }
      return;
    }
    errors.add(
        "Container stage '%s' command must be a string or list of strings".formatted(stageId));
  }

  private static void validateEnv(Map<?, ?> env, String stageId, List<String> errors) {
    if (env.size() > MAX_ENV_VARS) {
      errors.add(
          "Container stage '%s' env exceeds maximum of %d variables"
              .formatted(stageId, MAX_ENV_VARS));
    }
    for (Map.Entry<?, ?> entry : env.entrySet()) {
      String name = entry.getKey() != null ? entry.getKey().toString() : "";
      if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        errors.add(
            "Container stage '%s' env key '%s' is not a valid environment variable name"
                .formatted(stageId, name));
      }
    }
  }

  private static void validateResources(Map<?, ?> resources, String stageId, List<String> errors) {
    Object memory = resources.get("memory");
    if (memory != null) {
      String memoryText = memory.toString().trim();
      if (memoryText.isBlank()) {
        errors.add("Container stage '%s' resources.memory must not be blank".formatted(stageId));
      } else {
        try {
          ContainerResourceParser.toDockerMemoryLimit(memoryText);
        } catch (IllegalArgumentException e) {
          errors.add(
              "Container stage '%s' resources.memory is invalid: %s"
                  .formatted(stageId, e.getMessage()));
        }
      }
    }

    Object cpus = resources.get("cpus");
    if (cpus != null) {
      try {
        ContainerResourceParser.toDockerCpuLimit(cpus.toString());
      } catch (IllegalArgumentException e) {
        errors.add(
            "Container stage '%s' resources.cpus is invalid: %s"
                .formatted(stageId, e.getMessage()));
      }
    }
  }

  private static String getString(Map<?, ?> map, String key) {
    Object v = map.get(key);
    return v != null ? v.toString() : null;
  }
}
