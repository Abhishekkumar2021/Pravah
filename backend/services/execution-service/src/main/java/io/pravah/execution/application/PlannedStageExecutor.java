package io.pravah.execution.application;

import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Placeholder executor for stage types validated at publish but not yet runnable (US-01.03).
 *
 * <p>Returns a deterministic failure so operators see a clear message instead of falling through to
 * echo.
 */
@Component
public class PlannedStageExecutor implements EmbeddedStageExecutor {

  @Override
  public StageExecutionResult execute(JobEntity job, ExecutionEntity execution) {
    Map<String, Object> definition = execution.getDefinitionSnapshot();
    String stageType = resolveStageType(definition, job.getStageId());
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("stageId", job.getStageId());
    output.put("stageType", stageType);
    output.put(
        "message",
        "Stage type '%s' is defined in the pipeline but execution is not available yet"
            .formatted(stageType));
    return new StageExecutionResult(1, output);
  }

  private static String resolveStageType(Map<String, Object> definition, String stageId) {
    if (definition == null) {
      return "unknown";
    }
    Object stagesObj = definition.get("stages");
    if (!(stagesObj instanceof java.util.List<?> stages)) {
      return "unknown";
    }
    for (Object stageObj : stages) {
      if (stageObj instanceof Map<?, ?> stage) {
        Object id = stage.get("id");
        if (id != null && stageId.equals(id.toString())) {
          Object type = stage.get("type");
          return type != null ? type.toString().toLowerCase() : "unknown";
        }
      }
    }
    return "unknown";
  }
}
