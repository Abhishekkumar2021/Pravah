package io.pravah.common.domain;

import io.pravah.common.domain.resolution.StageOutputRef;
import io.pravah.common.domain.resolution.ValueReferenceParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@code ${stages.stageId.output.key}} references at pipeline publish time (US-02.10).
 *
 * <p>Scans each stage definition (including fields outside {@code config}) for {@code
 * ${stages.stageId.output.key}} strings. Ensures referenced stages exist, are not self-references,
 * and are upstream dependencies (directly or transitively via {@code dependsOn} / {@code
 * depends_on}).
 */
public final class StageOutputReferenceValidator {

  private StageOutputReferenceValidator() {}

  /**
   * Validates stage output references in the pipeline definition.
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

    Map<String, List<String>> dependsOnByStage = new HashMap<>();
    Map<String, Map<String, Object>> stageBodyById = new HashMap<>();
    Set<String> stageIds = new HashSet<>();
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < stages.size(); i++) {
      Object stageObj = stages.get(i);
      if (!(stageObj instanceof Map<?, ?> stage)) {
        continue;
      }

      String stageId = stringField(stage, "id");
      if (stageId == null || stageId.isBlank()) {
        errors.add("Stage at index %d is missing required 'id' field".formatted(i));
        continue;
      }

      if (!stageIds.add(stageId)) {
        errors.add("Duplicate stage id '%s'".formatted(stageId));
        continue;
      }

      dependsOnByStage.put(stageId, parseDependsOn(stage));
      stageBodyById.put(stageId, toStringKeyMap(stage));
    }

    errors.addAll(validateDependsOnTargets(dependsOnByStage, stageIds));
    errors.addAll(detectDependencyCycles(dependsOnByStage));

    for (Map.Entry<String, Map<String, Object>> entry : stageBodyById.entrySet()) {
      String consumerStageId = entry.getKey();
      Set<String> upstream = transitiveUpstream(consumerStageId, dependsOnByStage);
      List<StageOutputRef> refs = extractStageOutputRefs(entry.getValue());

      for (StageOutputRef ref : refs) {
        String referencedStageId = ref.stageId();
        if (!stageIds.contains(referencedStageId)) {
          errors.add(
              "Stage '%s' references unknown stage '%s' in %s"
                  .formatted(consumerStageId, referencedStageId, ref.raw()));
          continue;
        }
        if (referencedStageId.equals(consumerStageId)) {
          errors.add(
              "Stage '%s' cannot reference its own output (%s)"
                  .formatted(consumerStageId, ref.raw()));
          continue;
        }
        if (!upstream.contains(referencedStageId)) {
          errors.add(
              "Stage '%s' references output of '%s' but '%s' is not an upstream dependency (%s)"
                  .formatted(consumerStageId, referencedStageId, referencedStageId, ref.raw()));
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }
  }

  private static List<StageOutputRef> extractStageOutputRefs(Map<String, Object> config) {
    List<StageOutputRef> refs = new ArrayList<>();
    collectStageOutputRefs(config, refs);
    return refs;
  }

  private static void collectStageOutputRefs(Object value, List<StageOutputRef> refs) {
    if (value instanceof String s) {
      refs.addAll(ValueReferenceParser.extractStageOutputRefs(s));
    } else if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        collectStageOutputRefs(entry.getValue(), refs);
      }
    } else if (value instanceof List<?> list) {
      for (Object item : list) {
        collectStageOutputRefs(item, refs);
      }
    }
  }

  private static Set<String> transitiveUpstream(
      String stageId, Map<String, List<String>> dependsOnByStage) {
    Set<String> upstream = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>(dependsOnByStage.getOrDefault(stageId, List.of()));
    while (!queue.isEmpty()) {
      String dep = queue.poll();
      if (upstream.add(dep)) {
        queue.addAll(dependsOnByStage.getOrDefault(dep, List.of()));
      }
    }
    return upstream;
  }

  private static List<String> validateDependsOnTargets(
      Map<String, List<String>> dependsOnByStage, Set<String> stageIds) {
    List<String> errors = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : dependsOnByStage.entrySet()) {
      for (String dep : entry.getValue()) {
        if (!stageIds.contains(dep)) {
          errors.add("Stage '%s' depends on unknown stage '%s'".formatted(entry.getKey(), dep));
        }
      }
    }
    return errors;
  }

  private static List<String> detectDependencyCycles(Map<String, List<String>> dependsOnByStage) {
    List<String> errors = new ArrayList<>();
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();

    for (String stageId : dependsOnByStage.keySet()) {
      if (!visited.contains(stageId)) {
        detectCycleFrom(stageId, dependsOnByStage, visiting, visited, errors);
      }
    }
    return errors;
  }

  private static void detectCycleFrom(
      String stageId,
      Map<String, List<String>> dependsOnByStage,
      Set<String> visiting,
      Set<String> visited,
      List<String> errors) {
    if (visiting.contains(stageId)) {
      errors.add("Circular stage dependency detected involving stage '%s'".formatted(stageId));
      return;
    }
    if (visited.contains(stageId)) {
      return;
    }

    visiting.add(stageId);
    for (String dep : dependsOnByStage.getOrDefault(stageId, List.of())) {
      detectCycleFrom(dep, dependsOnByStage, visiting, visited, errors);
    }
    visiting.remove(stageId);
    visited.add(stageId);
  }

  private static List<String> parseDependsOn(Map<?, ?> stage) {
    Object dep = stage.get("dependsOn");
    if (dep == null) {
      dep = stage.get("depends_on");
    }
    if (!(dep instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    return list.stream().map(Object::toString).toList();
  }

  private static String stringField(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringKeyMap(Map<?, ?> stage) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<?, ?> entry : stage.entrySet()) {
      if (entry.getKey() != null) {
        result.put(entry.getKey().toString(), entry.getValue());
      }
    }
    return result;
  }
}
