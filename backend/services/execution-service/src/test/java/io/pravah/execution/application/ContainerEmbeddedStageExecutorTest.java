package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.execution.application.port.ContainerLogLineConsumer;
import io.pravah.execution.application.port.ContainerRunRequest;
import io.pravah.execution.application.port.ContainerRunResult;
import io.pravah.execution.application.port.ContainerRuntime;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContainerEmbeddedStageExecutor")
class ContainerEmbeddedStageExecutorTest {

  @Mock private JobLogService jobLogService;
  @Mock private ContainerRuntime containerRuntime;
  @Mock private ExecutionStageConfigResolver configResolver;

  private ContainerEmbeddedStageExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new ContainerEmbeddedStageExecutor(jobLogService, containerRuntime, configResolver);
  }

  @Test
  void runtimeUnavailable_returnsErrorExitCode() throws Exception {
    when(containerRuntime.isAvailable()).thenReturn(false);

    JobEntity job = job("run");
    ExecutionEntity execution = execution();
    Map<String, Object> stage =
        Map.of("id", "run", "type", "container", "config", Map.of("image", "alpine:3.19"));

    EmbeddedStageExecutor.StageExecutionResult result = executor.execute(job, execution, stage);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).containsKey("error");
  }

  @Test
  void successfulRun_returnsZeroExitCode() throws Exception {
    when(containerRuntime.isAvailable()).thenReturn(true);
    when(configResolver.resolveConfig(any(), any()))
        .thenReturn(Map.of("image", "alpine:3.19", "command", List.of("echo", "ok")));

    when(containerRuntime.run(any(), any()))
        .thenAnswer(
            invocation -> {
              ContainerLogLineConsumer consumer = invocation.getArgument(1);
              consumer.onLine("ok", false);
              return new ContainerRunResult(0, false);
            });

    JobEntity job = job("run");
    ExecutionEntity execution = execution();

    Map<String, Object> stage =
        Map.of(
            "id",
            "run",
            "type",
            "container",
            "config",
            Map.of("image", "alpine:3.19", "command", List.of("echo", "ok")));

    EmbeddedStageExecutor.StageExecutionResult result = executor.execute(job, execution, stage);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.output()).containsEntry("exit_code", 0);
    assertThat(result.output()).containsEntry("image", "alpine:3.19");

    ArgumentCaptor<ContainerRunRequest> requestCaptor =
        ArgumentCaptor.forClass(ContainerRunRequest.class);
    verify(containerRuntime).run(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue().image()).isEqualTo("alpine:3.19");
    assertThat(requestCaptor.getValue().command()).containsExactly("echo", "ok");
    verify(jobLogService).append(any(), eq(JobLogLevel.INFO), eq("ok"));
  }

  @Test
  void nonZeroExitCode_propagates() throws Exception {
    when(containerRuntime.isAvailable()).thenReturn(true);
    when(configResolver.resolveConfig(any(), any())).thenReturn(Map.of("image", "alpine:3.19"));
    when(containerRuntime.run(any(), any())).thenReturn(new ContainerRunResult(2, false));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(
            job("run"),
            execution(),
            Map.of("id", "run", "type", "container", "config", Map.of("image", "alpine:3.19")));

    assertThat(result.exitCode()).isEqualTo(2);
    verify(jobLogService).append(any(), eq(JobLogLevel.ERROR), any());
  }

  @Test
  void secretResolutionFails_returnsErrorExitCode() throws Exception {
    when(containerRuntime.isAvailable()).thenReturn(true);
    when(configResolver.resolveConfig(any(), any()))
        .thenThrow(new IllegalStateException("Secret not found: api_key"));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(
            job("run"),
            execution(),
            Map.of(
                "id",
                "run",
                "type",
                "container",
                "config",
                Map.of("image", "alpine:3.19", "env", Map.of("KEY", "${secret.api_key}"))));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output().get("error")).asString().contains("resolve");
    verify(jobLogService).append(any(), eq(JobLogLevel.ERROR), any());
  }

  private static JobEntity job(String stageId) throws Exception {
    JobEntity entity =
        JobEntity.builder()
            .executionId(UUID.randomUUID())
            .stageId(stageId)
            .stageName(stageId)
            .build();
    setId(entity, UUID.randomUUID());
    return entity;
  }

  private static ExecutionEntity execution() throws Exception {
    ExecutionEntity entity =
        ExecutionEntity.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(Map.of("stages", List.of()))
            .build();
    setId(entity, UUID.randomUUID());
    return entity;
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field field = entity.getClass().getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}
