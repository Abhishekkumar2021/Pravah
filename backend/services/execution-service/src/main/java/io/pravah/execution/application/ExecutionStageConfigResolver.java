package io.pravah.execution.application;

import io.pravah.common.domain.PipelineDefinitionResolver;
import io.pravah.common.domain.VariableContextResolver;
import io.pravah.common.domain.resolution.BuiltinResolverProvider;
import io.pravah.common.domain.resolution.EnvResolverProvider;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.UnifiedValueResolver;
import io.pravah.common.domain.resolution.VariableResolverProvider;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves deferred value references in stage configuration at execution time.
 *
 * <p>Variables are substituted when the execution is materialized; this resolver handles:
 *
 * <ul>
 *   <li>{@code ${secret.*}} — tenant secrets (US-02.17)
 *   <li>{@code ${stages.*.output.*}} — upstream stage outputs (US-02.10)
 *   <li>{@code env:} — environment variables
 * </ul>
 */
@Component
public class ExecutionStageConfigResolver {

  private final UnifiedValueResolver valueResolver;

  public ExecutionStageConfigResolver(
      HttpSecretResolverProvider secretResolverProvider,
      StageOutputResolverProvider stageOutputResolverProvider) {
    this.valueResolver =
        new UnifiedValueResolver(
            List.of(
                new BuiltinResolverProvider(),
                new VariableResolverProvider(),
                stageOutputResolverProvider,
                secretResolverProvider,
                new EnvResolverProvider()));
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> resolveConfig(
      ExecutionEntity execution, Map<String, Object> stageConfig) {
    if (stageConfig == null || stageConfig.isEmpty()) {
      return Map.of();
    }
    Instant executionTime =
        execution.getStartedAt() != null ? execution.getStartedAt() : Instant.now();
    Map<String, Object> variableContext =
        VariableContextResolver.resolve(
            execution.getDefinitionSnapshot(),
            PipelineDefinitionResolver.extractEnvironment(execution.getParameters()),
            execution.getParameters(),
            execution.getPipelineId(),
            execution.getPipelineVersion(),
            execution.getId(),
            executionTime);

    Map<String, Object> contextWithMeta = new LinkedHashMap<>(variableContext);
    contextWithMeta.put("__pipeline_id", execution.getPipelineId().toString());
    contextWithMeta.put("__pipeline_version", execution.getPipelineVersion());

    ResolutionContext ctx =
        ResolutionContext.forExecution(
            execution.getTenantId(), execution.getId(), executionTime, contextWithMeta);

    return valueResolver.resolveConfig(stageConfig, ctx);
  }
}
