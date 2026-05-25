package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.pravah.common.domain.JobState;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.SecretRef;
import io.pravah.common.domain.resolution.StageOutputRef;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StageOutputResolverProvider")
class StageOutputResolverProviderTest {

  @Mock private JobEntityRepository jobRepository;

  private StageOutputResolverProvider provider;
  private UUID tenantId;
  private UUID executionId;

  @BeforeEach
  void setUp() {
    provider = new StageOutputResolverProvider(jobRepository);
    tenantId = UUID.randomUUID();
    executionId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("supports")
  class SupportsTests {
    @Test
    void returnsTrue_forStageOutputRef() {
      assertThat(provider.supports(new StageOutputRef("stage", "key"))).isTrue();
    }

    @Test
    void returnsFalse_forOtherRefs() {
      assertThat(provider.supports(new SecretRef("secret"))).isFalse();
    }
  }

  @Nested
  @DisplayName("resolve")
  class ResolveTests {

    @Test
    void resolvesTopLevelKey() throws Exception {
      JobEntity job = succeededJob("extract", Map.of("row_count", 42, "columns", List.of("id")));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "row_count");

      Object result = provider.resolve(ref, ctx);

      assertThat(result).isEqualTo(42);
    }

    @Test
    void resolvesNestedMapKey() throws Exception {
      JobEntity job =
          succeededJob(
              "query",
              Map.of(
                  "metadata", Map.of("source", "postgres", "table", "orders"), "row_count", 100));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "query"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("query", "metadata.source");

      Object result = provider.resolve(ref, ctx);

      assertThat(result).isEqualTo("postgres");
    }

    @Test
    void resolvesListIndex() throws Exception {
      JobEntity job =
          succeededJob(
              "query",
              Map.of(
                  "preview",
                  List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob"))));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "query"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();

      Object firstId = provider.resolve(new StageOutputRef("query", "preview.0.id"), ctx);
      Object secondName = provider.resolve(new StageOutputRef("query", "preview.1.name"), ctx);

      assertThat(firstId).isEqualTo(1);
      assertThat(secondName).isEqualTo("Bob");
    }

    @Test
    void cachesResolvedValues() throws Exception {
      JobEntity job = succeededJob("extract", Map.of("count", 42));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "count");

      provider.resolve(ref, ctx);
      Object cached = ctx.stageOutputCache().get("extract:count");

      assertThat(cached).isEqualTo(42);
    }

    @Test
    void usesCachedValue() throws Exception {
      ResolutionContext ctx = executionContext();
      ctx.stageOutputCache().put("cached:value", "from-cache");

      StageOutputRef ref = new StageOutputRef("cached", "value");
      Object result = provider.resolve(ref, ctx);

      assertThat(result).isEqualTo("from-cache");
    }

    @Test
    void throws_whenStageNotFound() {
      when(jobRepository.findByExecutionIdAndStageId(executionId, "missing"))
          .thenReturn(Optional.empty());

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("missing", "key");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Stage 'missing' not found in execution");
    }

    @Test
    void throws_whenStageNotSucceeded() throws Exception {
      JobEntity job = runningJob("extract");
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "key");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot access output of stage 'extract' in state RUNNING");
    }

    @Test
    void throws_whenStageFailed() throws Exception {
      JobEntity job = failedJob("extract");
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "key");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot access output of stage 'extract' in state FAILED");
    }

    @Test
    void throws_whenOutputIsNull() throws Exception {
      JobEntity job = succeededJobWithNullOutput("extract");
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "key");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Stage 'extract' has no output data");
    }

    @Test
    void throws_whenKeyNotFound() throws Exception {
      JobEntity job = succeededJob("extract", Map.of("other", "value"));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "extract"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("extract", "missing");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("does not contain key 'missing'");
    }

    @Test
    void throws_whenListIndexOutOfBounds() throws Exception {
      JobEntity job = succeededJob("query", Map.of("items", List.of("a", "b")));
      when(jobRepository.findByExecutionIdAndStageId(executionId, "query"))
          .thenReturn(Optional.of(job));

      ResolutionContext ctx = executionContext();
      StageOutputRef ref = new StageOutputRef("query", "items.5");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("List index 5 out of bounds (size 2)");
    }

    @Test
    void throws_whenNoExecutionContext() {
      ResolutionContext ctx = ResolutionContext.forValidation(tenantId);
      StageOutputRef ref = new StageOutputRef("stage", "key");

      assertThatThrownBy(() -> provider.resolve(ref, ctx))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("without an execution context");
    }
  }

  private ResolutionContext executionContext() {
    return ResolutionContext.forExecution(tenantId, executionId, Instant.now(), Map.of());
  }

  private JobEntity succeededJob(String stageId, Map<String, Object> output) throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(executionId)
            .stageId(stageId)
            .stageName(stageId)
            .status(JobState.PENDING)
            .build();
    setId(job, UUID.randomUUID());

    job.queue();
    job.assign(UUID.randomUUID());
    job.succeed(0, output, null);

    return job;
  }

  private JobEntity succeededJobWithNullOutput(String stageId) throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(executionId)
            .stageId(stageId)
            .stageName(stageId)
            .status(JobState.PENDING)
            .build();
    setId(job, UUID.randomUUID());

    job.queue();
    job.assign(UUID.randomUUID());
    job.succeed(0, null, null);

    return job;
  }

  private JobEntity runningJob(String stageId) throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(executionId)
            .stageId(stageId)
            .stageName(stageId)
            .status(JobState.PENDING)
            .build();
    setId(job, UUID.randomUUID());

    job.queue();
    job.assign(UUID.randomUUID());

    return job;
  }

  private JobEntity failedJob(String stageId) throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(executionId)
            .stageId(stageId)
            .stageName(stageId)
            .status(JobState.PENDING)
            .build();
    setId(job, UUID.randomUUID());

    job.queue();
    job.assign(UUID.randomUUID());
    job.fail(1, "Error", false);

    return job;
  }

  private static void setId(JobEntity entity, UUID id) throws Exception {
    Field field = JobEntity.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}
