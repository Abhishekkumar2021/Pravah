package io.pravah.execution.application;

import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes job execution to the appropriate stage executor based on stage type (US-02.14).
 *
 * <p>Supported stage types:
 *
 * <ul>
 *   <li>{@code sql} — executes SQL query via JDBC
 *   <li>{@code echo} — dev/test executor (default fallback)
 * </ul>
 *
 * <p>Future: {@code python}, {@code container}, {@code dbt}, {@code spark}
 */
@Component
public class StageExecutorRouter implements EmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(StageExecutorRouter.class);

  private final SqlEmbeddedStageExecutor sqlExecutor;
  private final EchoEmbeddedStageExecutor echoExecutor;

  public StageExecutorRouter(
      SqlEmbeddedStageExecutor sqlExecutor, EchoEmbeddedStageExecutor echoExecutor) {
    this.sqlExecutor = sqlExecutor;
    this.echoExecutor = echoExecutor;
  }

  @Override
  public StageExecutionResult execute(JobEntity job, ExecutionEntity execution) {
    Map<String, Object> definition = execution.getDefinitionSnapshot();
    Map<String, Object> stageConfig = findStageConfig(definition, job.getStageId());
    String stageType = resolveStageType(stageConfig);

    log.debug("Routing stage execution: stageId={}, type={}", job.getStageId(), stageType);

    return switch (stageType) {
      case "sql" -> sqlExecutor.execute(job, execution, stageConfig);
      default -> echoExecutor.execute(job, execution);
    };
  }

  private static String resolveStageType(Map<String, Object> stageConfig) {
    if (stageConfig == null) {
      return "echo";
    }
    Object typeObj = stageConfig.get("type");
    return typeObj != null ? typeObj.toString().toLowerCase() : "echo";
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> findStageConfig(
      Map<String, Object> definition, String stageId) {
    if (definition == null) {
      return null;
    }
    Object stagesObj = definition.get("stages");
    if (!(stagesObj instanceof List<?> stages)) {
      return null;
    }
    for (Object stageObj : stages) {
      if (stageObj instanceof Map<?, ?> stage) {
        Object id = stage.get("id");
        if (id != null && stageId.equals(id.toString())) {
          return (Map<String, Object>) stage;
        }
      }
    }
    return null;
  }
}
