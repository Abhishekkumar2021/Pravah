package io.pravah.execution.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Extracts executable stage plans from a published pipeline definition JSON object. */
public final class StagePlanner {

  private StagePlanner() {}

  /**
   * Returns stage ids that have no dependencies (roots of the stage DAG).
   *
   * <p>Stages without a {@code dependsOn} field (or an empty list) are treated as roots. When no
   * stage declares {@code dependsOn}, every stage is a root (parallel fan-out until dependencies
   * exist in definitions).
   */
  public static List<String> rootStageIds(Map<String, Object> definition) {
    List<StageDef> stages = parseStages(definition);
    if (stages.isEmpty()) {
      return List.of();
    }
    boolean anyDependsOn = stages.stream().anyMatch(s -> !s.dependsOn().isEmpty());
    Set<String> roots = new LinkedHashSet<>();
    for (StageDef stage : stages) {
      if (!anyDependsOn || stage.dependsOn().isEmpty()) {
        roots.add(stage.id());
      }
    }
    return List.copyOf(roots);
  }

  /**
   * Stage ids that may leave {@code PENDING} for {@code QUEUED} after the given stages have
   * succeeded: every declared {@code dependsOn} entry is in {@code succeededStageIds}, and the
   * stage itself is not yet in {@code succeededStageIds}.
   *
   * <p>Caller should still verify the corresponding job is {@code PENDING} before queueing.
   */
  public static List<String> stagesReadyToQueueAfterSuccesses(
      Map<String, Object> definition, Set<String> succeededStageIds) {
    List<StageDef> stages = parseStages(definition);
    List<String> ready = new ArrayList<>();
    for (StageDef stage : stages) {
      if (succeededStageIds.contains(stage.id())) {
        continue;
      }
      if (stage.dependsOn().stream().allMatch(succeededStageIds::contains)) {
        ready.add(stage.id());
      }
    }
    return List.copyOf(ready);
  }

  public static List<PlannedJob> plan(Map<String, Object> definition) {
    List<StageDef> stages = parseStages(definition);
    List<PlannedJob> jobs = new ArrayList<>();
    for (StageDef stage : stages) {
      jobs.add(new PlannedJob(stage.id(), stage.name()));
    }
    return jobs;
  }

  private static List<StageDef> parseStages(Map<String, Object> definition) {
    Object raw = definition.get("stages");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<StageDef> result = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) {
        continue;
      }
      Object idObj = m.get("id");
      if (idObj == null) {
        continue;
      }
      String id = idObj.toString();
      Object nameObj = m.get("name");
      String name = nameObj != null ? nameObj.toString() : id;
      List<String> dependsOn = parseDependsOn(m.get("dependsOn"));
      result.add(new StageDef(id, name, dependsOn));
    }
    return result;
  }

  private static List<String> parseDependsOn(Object dep) {
    if (!(dep instanceof List<?> d) || d.isEmpty()) {
      return List.of();
    }
    return d.stream().map(Object::toString).toList();
  }

  /** Parsed stage definition from pipeline JSON. */
  private record StageDef(String id, String name, List<String> dependsOn) {}

  public record PlannedJob(String stageId, String stageName) {}
}
