package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.execution.application.port.PythonRunRequest;
import io.pravah.execution.application.port.PythonRunResult;
import io.pravah.execution.application.port.PythonRuntime;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PythonEmbeddedStageExecutor")
class PythonEmbeddedStageExecutorTest {

  @Mock private JobLogService jobLogService;
  @Mock private PythonRuntime pythonRuntime;
  @Mock private ExecutionStageConfigResolver configResolver;

  private PythonEmbeddedStageExecutor executor;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    executor =
        new PythonEmbeddedStageExecutor(
            jobLogService, pythonRuntime, configResolver, new ObjectMapper());
  }

  @Test
  void runtimeUnavailable_returnsErrorExitCode() throws Exception {
    when(pythonRuntime.isAvailable()).thenReturn(false);

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(
            job("run"),
            execution(),
            Map.of("id", "run", "type", "python", "config", Map.of("script", "print(1)")));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).containsKey("error");
  }

  @Test
  void successfulScript_parsesStructuredOutput() throws Exception {
    when(pythonRuntime.isAvailable()).thenReturn(true);
    when(configResolver.resolveConfig(any(), any()))
        .thenReturn(Map.of("script", "import json\nprint(json.dumps({'n': 2}))"));

    Path venvPython = tempDir.resolve("venv/bin/python");
    when(pythonRuntime.prepareWorkspace(any(), any(), any())).thenReturn(venvPython);
    when(pythonRuntime.run(any(), any()))
        .thenReturn(new PythonRunResult(0, false, "{\"n\": 2}\n", ""));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(
            job("run"),
            execution(),
            Map.of("id", "run", "type", "python", "config", Map.of("script", "print(1)")));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).containsEntry("exit_code", 0);
    @SuppressWarnings("unchecked")
    Map<String, Object> parsed = (Map<String, Object>) result.output().get("result");
    assertThat(parsed).containsEntry("n", 2);

    ArgumentCaptor<PythonRunRequest> requestCaptor =
        ArgumentCaptor.forClass(PythonRunRequest.class);
    verify(pythonRuntime).run(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue().command()).contains(venvPython.toString());
    verify(jobLogService, atLeastOnce()).append(any(), eq(JobLogLevel.INFO), any());
  }

  @Test
  void nonZeroExitCode_propagates() throws Exception {
    when(pythonRuntime.isAvailable()).thenReturn(true);
    when(configResolver.resolveConfig(any(), any()))
        .thenReturn(Map.of("script", "raise SystemExit(2)"));
    when(pythonRuntime.prepareWorkspace(any(), any(), any())).thenReturn(tempDir.resolve("py"));
    when(pythonRuntime.run(any(), any())).thenReturn(new PythonRunResult(2, false, "", "err"));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(
            job("run"),
            execution(),
            Map.of("id", "run", "type", "python", "config", Map.of("script", "x")));

    assertThat(result.exitCode()).isEqualTo(2);
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
