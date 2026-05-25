package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.pravah.common.runner.RemoteJobSpecEnv;
import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteJobSpecBuilderTest {

  @Mock private ExecutionStageConfigResolver configResolver;
  @Mock private ConnectionCatalog connectionCatalog;

  private RemoteJobSpecBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new RemoteJobSpecBuilder(configResolver, connectionCatalog);
  }

  @Test
  void build_container_includesImageAndResources() {
    UUID tenantId = UUID.randomUUID();
    ExecutionEntity execution = executionWithStages(List.of());
    JobEntity job = jobForStage(execution, "s1");

    Map<String, Object> stage =
        Map.of(
            "type",
            "container",
            "config",
            Map.of(
                "image",
                "python:3.12",
                "command",
                List.of("python", "-c", "print(1)"),
                "resources",
                Map.of("memory", "512Mi", "cpus", "1")));

    when(configResolver.resolveConfig(execution, stageConfig(stage)))
        .thenReturn(stageConfig(stage));

    RemoteJobSpecPayload spec = builder.build(job, execution, stage);

    assertThat(spec.executor()).isEqualTo("container");
    assertThat(spec.image()).isEqualTo("python:3.12");
    assertThat(spec.commands()).containsExactly("python", "-c", "print(1)");
    assertThat(spec.memoryBytes()).isEqualTo(512L * 1024 * 1024);
    assertThat(spec.cpuCores()).isEqualTo(1.0);
  }

  @Test
  void build_python_embedsScriptInEnv() {
    ExecutionEntity execution = executionWithStages(List.of());
    JobEntity job = jobForStage(execution, "s1");
    Map<String, Object> config =
        Map.of("script", "print('hi')", "requirements", List.of("requests==2.31.0"));
    Map<String, Object> stage = Map.of("type", "python", "config", config);

    when(configResolver.resolveConfig(execution, config)).thenReturn(config);

    RemoteJobSpecPayload spec = builder.build(job, execution, stage);

    assertThat(spec.executor()).isEqualTo("python");
    assertThat(spec.environment()).containsEntry(RemoteJobSpecEnv.PYTHON_SCRIPT, "print('hi')");
    assertThat(spec.environment().get(RemoteJobSpecEnv.PYTHON_REQUIREMENTS))
        .contains("requests==2.31.0");
  }

  @Test
  void build_sql_resolvesConnection() {
    UUID tenantId = UUID.randomUUID();
    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(tenantId)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .definitionSnapshot(Map.of("stages", List.of()))
            .build();
    JobEntity job = jobForStage(execution, "s1");
    Map<String, Object> config = Map.of("connection", "warehouse", "query", "SELECT 1");
    Map<String, Object> stage = Map.of("type", "sql", "config", config);

    when(configResolver.resolveConfig(execution, config)).thenReturn(config);
    when(connectionCatalog.resolve(tenantId, "warehouse"))
        .thenReturn(
            new ResolvedJdbcConnection(
                "warehouse", "jdbc:postgresql://localhost/db", "user", "secret"));

    RemoteJobSpecPayload spec = builder.build(job, execution, stage);

    assertThat(spec.executor()).isEqualTo("sql");
    assertThat(spec.environment().get(RemoteJobSpecEnv.SQL_JDBC_URL))
        .isEqualTo("jdbc:postgresql://localhost/db");
    assertThat(spec.environment().get(RemoteJobSpecEnv.SQL_QUERY)).isEqualTo("SELECT 1");
  }

  @Test
  void build_unsupportedType_throws() {
    ExecutionEntity execution = executionWithStages(List.of());
    JobEntity job = jobForStage(execution, "s1");
    Map<String, Object> stage = Map.of("type", "echo", "config", Map.of());

    when(configResolver.resolveConfig(execution, Map.of())).thenReturn(Map.of());

    assertThatThrownBy(() -> builder.build(job, execution, stage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not supported on remote runners");
  }

  private static JobEntity jobForStage(ExecutionEntity execution, String stageId) {
    UUID executionId = execution.getId() != null ? execution.getId() : UUID.randomUUID();
    return JobEntity.builder().executionId(executionId).stageId(stageId).stageName(stageId).build();
  }

  private static ExecutionEntity executionWithStages(List<Map<String, Object>> stages) {
    return ExecutionEntity.builder()
        .tenantId(UUID.randomUUID())
        .pipelineId(UUID.randomUUID())
        .pipelineVersion(1)
        .triggerType("manual")
        .definitionSnapshot(Map.of("stages", stages))
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> stageConfig(Map<String, Object> stage) {
    return (Map<String, Object>) stage.get("config");
  }
}
