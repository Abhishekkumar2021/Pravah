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
    Object raw = definition.get("stages");
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    boolean anyDependsOn = false;
    for (Object o : list) {
      if (o instanceof Map<?, ?> m && m.get("id") != null) {
        Object dep = m.get("dependsOn");
        if (dep instanceof List<?> d && !d.isEmpty()) {
          anyDependsOn = true;
          break;
        }
      }
    }
    Set<String> roots = new LinkedHashSet<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) {
        continue;
      }
      Object idObj = m.get("id");
      if (idObj == null) {
        continue;
      }
      String stageId = idObj.toString();
      if (!anyDependsOn) {
        roots.add(stageId);
        continue;
      }
      Object dep = m.get("dependsOn");
      if (!(dep instanceof List<?> d) || d.isEmpty()) {
        roots.add(stageId);
      }
    }
    return List.copyOf(roots);
  }

  public static List<PlannedJob> plan(Map<String, Object> definition) {
    Object raw = definition.get("stages");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<PlannedJob> jobs = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) {
        continue;
      }
      Object idObj = m.get("id");
      if (idObj == null) {
        continue;
      }
      String stageId = idObj.toString();
      Object nameObj = m.get("name");
      String stageName = nameObj != null ? nameObj.toString() : stageId;
      jobs.add(new PlannedJob(stageId, stageName));
    }
    return jobs;
  }

  public record PlannedJob(String stageId, String stageName) {}
}
