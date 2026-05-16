package io.pravah.common.domain;

import java.util.List;
import java.util.Map;

/** Parses and validates {@link StageTimeout} from pipeline definition JSON (US-02.07). */
public final class StageTimeoutParser {

  private StageTimeoutParser() {}

  public static StageTimeout parseFromMap(Object raw) {
    if (raw == null) {
      return StageTimeout.NONE;
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("timeout block must be a mapping");
    }
    return parseMapping(map);
  }

  /** Resolves timeout for a stage: pipeline default with optional per-stage override. */
  public static StageTimeout resolveForStage(Map<String, Object> definition, String stageId) {
    if (definition == null) {
      return StageTimeout.NONE;
    }
    StageTimeout pipeline = parsePipelineLevel(definition);
    Map<?, ?> stageMap = findStageMap(definition, stageId);
    if (stageMap == null) {
      return pipeline;
    }
    return mergePartial(pipeline, stageMap);
  }

  public static void validateDefinition(Map<String, Object> definition) {
    if (definition == null) {
      return;
    }
    parsePipelineLevel(definition);
    Object stages = definition.get("stages");
    if (!(stages instanceof List<?> list)) {
      return;
    }
    for (Object stage : list) {
      if (stage instanceof Map<?, ?> stageMap) {
        mergePartial(StageTimeout.NONE, stageMap);
      }
    }
  }

  private static StageTimeout parsePipelineLevel(Map<String, Object> definition) {
    if (definition.containsKey("timeout_seconds") || definition.containsKey("timeout_minutes")) {
      return parseMapping(definition);
    }
    return parseFromMap(definition.get("timeout"));
  }

  private static StageTimeout mergePartial(StageTimeout base, Map<?, ?> stageMap) {
    if (stageMap.containsKey("timeout")) {
      StageTimeout nested = parseFromMap(stageMap.get("timeout"));
      if (nested.isConfigured()) {
        return nested;
      }
    }
    boolean hasSeconds = stageMap.containsKey("timeout_seconds");
    boolean hasMinutes = stageMap.containsKey("timeout_minutes");
    if (!hasSeconds && !hasMinutes) {
      return base;
    }
    if (hasSeconds && hasMinutes) {
      throw new IllegalArgumentException(
          "stage timeout: use either timeout_seconds or timeout_minutes, not both");
    }
    int seconds =
        hasSeconds
            ? intField(stageMap, "timeout_seconds")
            : intField(stageMap, "timeout_minutes") * 60;
    return new StageTimeout(seconds);
  }

  private static StageTimeout parseMapping(Map<?, ?> map) {
    boolean hasSeconds = map.containsKey("timeout_seconds");
    boolean hasMinutes = map.containsKey("timeout_minutes");
    if (hasSeconds && hasMinutes) {
      throw new IllegalArgumentException(
          "timeout: use either timeout_seconds or timeout_minutes, not both");
    }
    Integer seconds = null;
    if (hasSeconds) {
      seconds = intField(map, "timeout_seconds");
    } else if (hasMinutes) {
      seconds = intField(map, "timeout_minutes") * 60;
    }
    return seconds == null ? StageTimeout.NONE : new StageTimeout(seconds);
  }

  private static Map<?, ?> findStageMap(Map<String, Object> definition, String stageId) {
    Object stages = definition.get("stages");
    if (!(stages instanceof List<?> list)) {
      return null;
    }
    for (Object o : list) {
      if (o instanceof Map<?, ?> m) {
        Object id = m.get("id");
        if (id != null && stageId.equals(id.toString())) {
          return m;
        }
      }
    }
    return null;
  }

  private static int intField(Map<?, ?> map, String key) {
    Object v = map.get(key);
    if (v instanceof Number n) {
      int value = n.intValue();
      if (value < 1) {
        throw new IllegalArgumentException("timeout." + key + " must be >= 1");
      }
      return value;
    }
    throw new IllegalArgumentException("timeout." + key + " must be a number");
  }
}
