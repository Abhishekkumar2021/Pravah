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
 *   <li>{@code container} — runs a Docker image (US-02.17)
 *   <li>{@code echo} — dev/test executor (default fallback)
 * </ul>
 *
 * <p>{@code python}, {@code dbt}, {@code spark} are validated at publish but routed to {@link
 * PlannedStageExecutor} until runner dispatch ships.
 */
@Component
public class StageExecutorRouter implements EmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(StageExecutorRouter.class);

  private final SqlEmbeddedStageExecutor sqlExecutor;
  private final ContainerEmbeddedStageExecutor containerExecutor;
  private final EchoEmbeddedStageExecutor echoExecutor;
  private final PlannedStageExecutor plannedStageExecutor;

  public StageExecutorRouter(
      SqlEmbeddedStageExecutor sqlExecutor,
      ContainerEmbeddedStageExecutor containerExecutor,
      EchoEmbeddedStageExecutor echoExecutor,
      PlannedStageExecutor plannedStageExecutor) {
    this.sqlExecutor = sqlExecutor;
    this.containerExecutor = containerExecutor;
    this.echoExecutor = echoExecutor;
    this.plannedStageExecutor = plannedStageExecutor;
  }

  @Override
  public StageExecutionResult execute(JobEntity job, ExecutionEntity execution) {
    Map<String, Object> definition = execution.getDefinitionSnapshot();
    Map<String, Object> stageConfig = findStageConfig(definition, job.getStageId());
    String stageType = resolveStageType(stageConfig);

    log.debug("Routing stage execution: stageId={}, type={}", job.getStageId(), stageType);

    return switch (stageType) {
      case "sql" -> sqlExecutor.execute(job, execution, stageConfig);
      case "container" -> containerExecutor.execute(job, execution, stageConfig);
      case "python", "dbt", "spark" -> plannedStageExecutor.execute(job, execution);
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
