package io.pravah.common.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses and validates {@link RetryPolicy} from pipeline definition JSON maps (US-02.06). */
public final class RetryPolicyParser {

  private RetryPolicyParser() {}

  public static RetryPolicy parseFromMap(Object raw) {
    if (raw == null) {
      return RetryPolicy.DEFAULT;
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("retry must be a mapping");
    }
    return parseMapping(map);
  }

  /**
   * Resolves retry policy for a stage: pipeline defaults with optional per-stage overrides (only
   * keys present in the stage block override the pipeline default).
   */
  public static RetryPolicy resolveForStage(Map<String, Object> definition, String stageId) {
    if (definition == null) {
      return RetryPolicy.DEFAULT;
    }
    RetryPolicy pipeline = parseFromMap(definition.get("retry"));
    Map<?, ?> stageMap = findStageMap(definition, stageId);
    if (stageMap == null) {
      return pipeline;
    }
    return mergePartial(pipeline, stageMap.get("retry"));
  }

  /** Validates pipeline-level and per-stage retry blocks when present. */
  public static void validateDefinition(Map<String, Object> definition) {
    if (definition == null) {
      return;
    }
    parseFromMap(definition.get("retry"));
    Object stages = definition.get("stages");
    if (!(stages instanceof List<?> list)) {
      return;
    }
    for (Object stage : list) {
      if (stage instanceof Map<?, ?> stageMap) {
        parseFromMap(stageMap.get("retry"));
      }
    }
  }

  private static RetryPolicy mergePartial(RetryPolicy base, Object rawOverride) {
    if (rawOverride == null) {
      return base;
    }
    if (!(rawOverride instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("stage retry must be a mapping");
    }
    int maxAttempts =
        map.containsKey("max_attempts")
            ? intField(map, "max_attempts", base.maxAttempts())
            : base.maxAttempts();
    int delaySeconds =
        map.containsKey("delay_seconds")
            ? intField(map, "delay_seconds", base.delaySeconds())
            : base.delaySeconds();
    double backoff =
        map.containsKey("backoff_multiplier")
            ? doubleField(map, "backoff_multiplier", base.backoffMultiplier())
            : base.backoffMultiplier();
    Set<Integer> exitCodes =
        map.containsKey("retry_on_exit_codes")
            ? intListField(map, "retry_on_exit_codes")
            : base.retryOnExitCodes();
    Integer maxDuration =
        map.containsKey("max_retry_duration_seconds")
            ? optionalIntField(map, "max_retry_duration_seconds")
            : base.maxRetryDurationSeconds();
    return new RetryPolicy(maxAttempts, delaySeconds, backoff, exitCodes, maxDuration);
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

  private static RetryPolicy parseMapping(Map<?, ?> map) {
    int maxAttempts = intField(map, "max_attempts", RetryPolicy.DEFAULT_MAX_ATTEMPTS);
    int delaySeconds = intField(map, "delay_seconds", 0);
    double backoffMultiplier = doubleField(map, "backoff_multiplier", 1.0);
    Set<Integer> exitCodes = intListField(map, "retry_on_exit_codes");
    Integer maxDuration = optionalIntField(map, "max_retry_duration_seconds");
    return new RetryPolicy(maxAttempts, delaySeconds, backoffMultiplier, exitCodes, maxDuration);
  }

  private static int intField(Map<?, ?> map, String key, int defaultValue) {
    if (!map.containsKey(key)) {
      return defaultValue;
    }
    Object v = map.get(key);
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException("retry." + key + " must be a number");
  }

  private static Integer optionalIntField(Map<?, ?> map, String key) {
    if (!map.containsKey(key)) {
      return null;
    }
    Object v = map.get(key);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException("retry." + key + " must be a number");
  }

  private static double doubleField(Map<?, ?> map, String key, double defaultValue) {
    if (!map.containsKey(key)) {
      return defaultValue;
    }
    Object v = map.get(key);
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    throw new IllegalArgumentException("retry." + key + " must be a number");
  }

  private static Set<Integer> intListField(Map<?, ?> map, String key) {
    if (!map.containsKey(key)) {
      return Set.of();
    }
    Object v = map.get(key);
    if (!(v instanceof List<?> list)) {
      throw new IllegalArgumentException("retry." + key + " must be a list of integers");
    }
    Set<Integer> codes = new LinkedHashSet<>();
    for (Object item : list) {
      if (item instanceof Number n) {
        codes.add(n.intValue());
      } else {
        throw new IllegalArgumentException("retry." + key + " must contain numbers only");
      }
    }
    return codes;
  }
}
