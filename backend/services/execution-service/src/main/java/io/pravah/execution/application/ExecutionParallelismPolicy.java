package io.pravah.execution.application;

import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Map;

/** Resolves and enforces per-execution parallel stage limits (US-02.09). */
public final class ExecutionParallelismPolicy {

  public static final String DEFINITION_MAX_PARALLEL_KEY = "maxParallelStages";

  private ExecutionParallelismPolicy() {}

  /**
   * Effective parallelism cap: {@code definition.execution.maxParallelStages} when set, else {@code
   * defaultMax}.
   */
  public static int resolveMaxParallelStages(Map<String, Object> definition, int defaultMax) {
    if (definition == null) {
      return defaultMax;
    }
    Object executionBlock = definition.get("execution");
    if (!(executionBlock instanceof Map<?, ?> execution)) {
      return defaultMax;
    }
    Object raw = execution.get(DEFINITION_MAX_PARALLEL_KEY);
    if (raw == null) {
      return defaultMax;
    }
    int value;
    if (raw instanceof Number n) {
      value = n.intValue();
    } else {
      try {
        value = Integer.parseInt(raw.toString().trim());
      } catch (NumberFormatException e) {
        return defaultMax;
      }
    }
    return value < 1 ? 1 : value;
  }

  /** Jobs already dispatched or executing (count toward the parallelism cap). */
  public static int countActiveJobs(List<JobEntity> jobs) {
    int active = 0;
    for (JobEntity job : jobs) {
      JobState status = job.getStatus();
      if (status == JobState.QUEUED || status == JobState.RUNNING) {
        active++;
      }
    }
    return active;
  }

  public static int availableSlots(int maxParallel, int activeJobs) {
    return Math.max(0, maxParallel - activeJobs);
  }
}
