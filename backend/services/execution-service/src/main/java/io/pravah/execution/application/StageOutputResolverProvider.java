package io.pravah.execution.application;

import io.pravah.common.domain.JobState;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.StageOutputRef;
import io.pravah.common.domain.resolution.ValueReference;
import io.pravah.common.domain.resolution.ValueResolverProvider;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code ${stages.stageId.output.key}} references by querying completed upstream jobs.
 *
 * <p>Stage outputs are resolved at execution time (deferred) from the jobs table. The referenced
 * stage must have already completed successfully within the same execution.
 *
 * <p>Supports nested path access using dot notation (e.g., {@code preview.0.id}).
 */
@Component
public class StageOutputResolverProvider implements ValueResolverProvider {

  private final JobEntityRepository jobRepository;

  public StageOutputResolverProvider(JobEntityRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof StageOutputRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    StageOutputRef stageRef = (StageOutputRef) ref;

    if (ctx.executionId() == null) {
      throw new IllegalStateException(
          "Cannot resolve stage output reference '%s' without an execution context"
              .formatted(stageRef.raw()));
    }

    String cacheKey = stageRef.stageId() + ":" + stageRef.outputPath();
    if (ctx.stageOutputCache().containsKey(cacheKey)) {
      return ctx.stageOutputCache().get(cacheKey);
    }

    JobEntity job =
        jobRepository
            .findByExecutionIdAndStageId(ctx.executionId(), stageRef.stageId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Stage '%s' not found in execution".formatted(stageRef.stageId())));

    if (job.getStatus() != JobState.SUCCEEDED) {
      throw new IllegalStateException(
          "Cannot access output of stage '%s' in state %s (expected SUCCEEDED)"
              .formatted(stageRef.stageId(), job.getStatus()));
    }

    Map<String, Object> output = job.getOutput();
    if (output == null || output.isEmpty()) {
      throw new IllegalStateException(
          "Stage '%s' has no output data".formatted(stageRef.stageId()));
    }

    Object value = extractNestedValue(output, stageRef.outputPath());
    ctx.stageOutputCache().put(cacheKey, value);
    return value;
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Runtime validation only — stage may not exist yet at publish time
    // Full validation happens when resolving at execution time
  }

  /**
   * Extracts a nested value from a map using dot notation path.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code row_count} → direct key lookup
   *   <li>{@code preview.0.id} → preview[0].id
   *   <li>{@code metadata.tags.0} → metadata.tags[0]
   * </ul>
   *
   * @param map the source map
   * @param path the dot-separated path
   * @return the value at the path
   * @throws IllegalStateException if the path is invalid or value not found
   */
  private static Object extractNestedValue(Map<String, Object> map, String path) {
    String[] parts = path.split("\\.");
    Object current = map;

    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      String pathSoFar = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i + 1));

      if (current == null) {
        throw new IllegalStateException(
            "Cannot access '%s': path '%s' is null".formatted(path, pathSoFar));
      }

      if (current instanceof Map<?, ?> m) {
        if (!m.containsKey(part)) {
          throw new IllegalStateException(
              "Stage output does not contain key '%s' (at path '%s')".formatted(part, pathSoFar));
        }
        current = m.get(part);
      } else if (current instanceof List<?> list) {
        int index;
        try {
          index = Integer.parseInt(part);
        } catch (NumberFormatException e) {
          throw new IllegalStateException(
              "Invalid list index '%s' at path '%s' (expected number)".formatted(part, pathSoFar));
        }
        if (index < 0 || index >= list.size()) {
          throw new IllegalStateException(
              "List index %d out of bounds (size %d) at path '%s'"
                  .formatted(index, list.size(), pathSoFar));
        }
        current = list.get(index);
      } else {
        throw new IllegalStateException(
            "Cannot access '%s' on value of type %s at path '%s'"
                .formatted(part, current.getClass().getSimpleName(), pathSoFar));
      }
    }

    return current;
  }
}
