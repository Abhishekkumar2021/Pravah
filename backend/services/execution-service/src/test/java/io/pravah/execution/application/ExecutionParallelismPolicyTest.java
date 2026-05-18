package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExecutionParallelismPolicyTest {

  private static final int DEFAULT_MAX = 4;

  @Nested
  class ResolveMaxParallelStages {

    @Test
    void returnsDefaultWhenDefinitionIsNull() {
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(null, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void returnsDefaultWhenExecutionBlockIsMissing() {
      Map<String, Object> definition = Map.of("stages", List.of());
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void returnsDefaultWhenExecutionBlockIsNotAMap() {
      Map<String, Object> definition = Map.of("execution", "not-a-map");
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void returnsDefaultWhenMaxParallelStagesIsMissing() {
      Map<String, Object> definition = Map.of("execution", Map.of("timeout", 300));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void returnsDefaultWhenMaxParallelStagesIsNull() {
      Map<String, Object> definition =
          Map.of(
              "execution",
              new java.util.HashMap<String, Object>() {
                {
                  put("maxParallelStages", null);
                }
              });
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void extractsIntegerFromExecutionBlock() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", 8));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(8);
    }

    @Test
    void extractsLongAndConvertsToInt() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", 6L));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(6);
    }

    @Test
    void extractsDoubleAndConvertsToInt() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", 5.7));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(5);
    }

    @Test
    void parsesStringValueToInt() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", "10"));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(10);
    }

    @Test
    void parsesStringWithWhitespace() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", "  12  "));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(12);
    }

    @Test
    void returnsDefaultForInvalidStringValue() {
      Map<String, Object> definition =
          Map.of("execution", Map.of("maxParallelStages", "not-a-number"));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @Test
    void returnsDefaultForEmptyString() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", ""));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(DEFAULT_MAX);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void clampsNonPositiveValuesToOne(int value) {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", value));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(1);
    }

    @Test
    void supportsLargeMaxValues() {
      Map<String, Object> definition = Map.of("execution", Map.of("maxParallelStages", 1000));
      int result = ExecutionParallelismPolicy.resolveMaxParallelStages(definition, DEFAULT_MAX);
      assertThat(result).isEqualTo(1000);
    }
  }

  @Nested
  class CountActiveJobs {

    private final UUID executionId = UUID.randomUUID();

    @Test
    void returnsZeroForEmptyList() {
      int result = ExecutionParallelismPolicy.countActiveJobs(List.of());
      assertThat(result).isZero();
    }

    @Test
    void countsQueuedJobsAsActive() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.QUEUED));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isEqualTo(1);
    }

    @Test
    void countsRunningJobsAsActive() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.RUNNING));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isEqualTo(1);
    }

    @Test
    void doesNotCountPendingJobs() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.PENDING));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isZero();
    }

    @Test
    void doesNotCountSucceededJobs() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.SUCCEEDED));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isZero();
    }

    @Test
    void doesNotCountFailedJobs() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.FAILED));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isZero();
    }

    @Test
    void doesNotCountCancelledJobs() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.CANCELLED));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isZero();
    }

    @Test
    void doesNotCountSkippedJobs() {
      List<JobEntity> jobs = List.of(jobWithStatus(JobState.SKIPPED));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isZero();
    }

    @Test
    void countsMixedStatesCorrectly() {
      List<JobEntity> jobs =
          List.of(
              jobWithStatus(JobState.PENDING),
              jobWithStatus(JobState.QUEUED),
              jobWithStatus(JobState.RUNNING),
              jobWithStatus(JobState.SUCCEEDED),
              jobWithStatus(JobState.FAILED),
              jobWithStatus(JobState.QUEUED),
              jobWithStatus(JobState.RUNNING));
      int result = ExecutionParallelismPolicy.countActiveJobs(jobs);
      assertThat(result).isEqualTo(4);
    }

    private JobEntity jobWithStatus(JobState status) {
      return JobEntity.builder()
          .executionId(executionId)
          .stageId("stage-" + UUID.randomUUID())
          .stageName("Stage")
          .status(status)
          .build();
    }
  }

  @Nested
  class AvailableSlots {

    @Test
    void returnsMaxWhenNoActiveJobs() {
      int result = ExecutionParallelismPolicy.availableSlots(4, 0);
      assertThat(result).isEqualTo(4);
    }

    @Test
    void returnsDifferenceWhenBelowCap() {
      int result = ExecutionParallelismPolicy.availableSlots(4, 2);
      assertThat(result).isEqualTo(2);
    }

    @Test
    void returnsZeroWhenAtCap() {
      int result = ExecutionParallelismPolicy.availableSlots(4, 4);
      assertThat(result).isZero();
    }

    @Test
    void returnsZeroWhenOverCap() {
      int result = ExecutionParallelismPolicy.availableSlots(4, 6);
      assertThat(result).isZero();
    }

    @Test
    void handlesEdgeCaseWithMaxParallelOne() {
      assertThat(ExecutionParallelismPolicy.availableSlots(1, 0)).isEqualTo(1);
      assertThat(ExecutionParallelismPolicy.availableSlots(1, 1)).isZero();
    }
  }
}
