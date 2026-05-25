package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DbtEmbeddedStageExecutor")
class DbtEmbeddedStageExecutorTest {

  @Mock private JobLogService jobLogService;
  @Mock private ExecutionStageConfigResolver configResolver;

  private DbtEmbeddedStageExecutor executor;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    executor = new DbtEmbeddedStageExecutor(jobLogService, configResolver);
  }

  @Test
  void missingProjectDir_returnsError() throws Exception {
    when(configResolver.resolveConfig(any(), eq(Map.of()))).thenReturn(Map.of());

    var result =
        executor.execute(
            job(), execution(), Map.of("id", "dbt", "type", "dbt", "config", Map.of()));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).containsKey("error");
  }

  @Test
  void invalidProjectDir_returnsError() throws Exception {
    when(configResolver.resolveConfig(any(), any()))
        .thenReturn(Map.of("project_dir", tempDir.resolve("missing").toString()));

    var result =
        executor.execute(
            job(),
            execution(),
            Map.of("id", "dbt", "type", "dbt", "config", Map.of("project_dir", "x")));

    assertThat(result.exitCode()).isEqualTo(1);
  }

  @Test
  void validProjectDir_invokesDbt() throws Exception {
    Path project = tempDir.resolve("dbt_project");
    Files.createDirectories(project);

    when(configResolver.resolveConfig(any(), any()))
        .thenReturn(
            Map.of(
                "project_dir", project.toString(),
                "dbt_binary", "echo",
                "select", "all"));

    var result =
        executor.execute(
            job(), execution(), Map.of("id", "dbt", "type", "dbt", "config", Map.of()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).containsEntry("executor", "dbt");
  }

  private static JobEntity job() throws Exception {
    JobEntity job =
        JobEntity.builder().executionId(UUID.randomUUID()).stageId("dbt").stageName("dbt").build();
    setId(job, UUID.randomUUID());
    return job;
  }

  private static ExecutionEntity execution() throws Exception {
    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .build();
    setId(execution, UUID.randomUUID());
    return execution;
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field field = entity.getClass().getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}
