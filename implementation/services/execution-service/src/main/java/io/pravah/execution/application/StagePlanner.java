package io.pravah.execution.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Extracts executable stage plans from a published pipeline definition JSON object. */
public final class StagePlanner {

  private StagePlanner() {}

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
