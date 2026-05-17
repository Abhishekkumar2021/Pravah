package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlEmbeddedStageExecutor")
class SqlEmbeddedStageExecutorTest {

  @Mock private JobLogService jobLogService;
  @Mock private ConnectionCatalog connectionCatalog;
  @Mock private DataSource defaultDataSource;
  @Mock private ExecutionStageConfigResolver configResolver;
  @Mock private Connection connection;
  @Mock private Statement statement;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData resultSetMetaData;

  private SqlEmbeddedStageExecutor executor;

  @BeforeEach
  void setUp() {
    executor =
        new SqlEmbeddedStageExecutor(
            jobLogService, connectionCatalog, defaultDataSource, configResolver);
    when(configResolver.resolveConfig(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
  }

  @Test
  @DisplayName("missing query returns error exit code")
  void missingQuery_returnsErrorExitCode() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("connection", Map.of("url", "jdbc:postgresql://localhost/db")));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .containsEntry("error", "SQL stage missing required 'query' in config");
    verify(jobLogService).append(any(), eq(JobLogLevel.ERROR), any());
  }

  @Test
  @DisplayName("blank query returns error exit code")
  void blankQuery_returnsErrorExitCode() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("query", "   "));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .containsEntry("error", "SQL stage missing required 'query' in config");
  }

  @Test
  @DisplayName("successful SELECT query returns success with row count and preview")
  void successfulSelectQuery_returnsSuccess() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("query", "SELECT id, name FROM users"));

    when(defaultDataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.execute("SELECT id, name FROM users")).thenReturn(true);
    when(statement.getResultSet()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(2);
    when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
    when(resultSetMetaData.getColumnLabel(2)).thenReturn("name");
    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getObject(1)).thenReturn(1, 2);
    when(resultSet.getObject(2)).thenReturn("Alice", "Bob");

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.output()).containsEntry("executor", "sql");
    assertThat(result.output()).containsEntry("row_count", 2);
    assertThat(result.output()).containsEntry("query_type", "SELECT");
    assertThat(result.output()).containsEntry("columns", List.of("id", "name"));
    assertThat(result.output()).containsKey("preview");
    assertThat(result.output()).containsKey("duration_ms");
  }

  @Test
  @DisplayName("successful UPDATE query returns update count")
  void successfulUpdateQuery_returnsUpdateCount() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("query", "UPDATE users SET active = true"));

    when(defaultDataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.execute("UPDATE users SET active = true")).thenReturn(false);
    when(statement.getUpdateCount()).thenReturn(42);

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.output()).containsEntry("row_count", 42);
    assertThat(result.output()).containsEntry("query_type", "UPDATE/INSERT/DELETE");
  }

  @Test
  @DisplayName("SQL exception returns error exit code with details")
  void sqlException_returnsErrorExitCode() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("query", "INVALID SQL"));

    when(defaultDataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.execute("INVALID SQL")).thenThrow(new SQLException("syntax error", "42601", 1));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).doesNotContainKey("sql_state");
    assertThat(result.output()).doesNotContainKey("error_code");
    assertThat((String) result.output().get("error"))
        .isEqualTo("SQL execution failed. Check logs for details.");
    verify(jobLogService).append(any(), eq(JobLogLevel.ERROR), any());
  }

  @Test
  @DisplayName("uses inline connection config when provided")
  @SuppressWarnings("unchecked")
  void usesInlineConnectionConfig() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config",
                Map.of(
                    "query",
                    "SELECT 1",
                    "connection",
                    Map.of(
                        "url", "jdbc:postgresql://other-host:5432/otherdb",
                        "username", "otheruser",
                        "password", "otherpass")));

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).containsKey("error");
  }

  @Test
  @DisplayName("uses default datasource when no connection config")
  void usesDefaultDatasource() throws Exception {
    JobEntity job = createJob();
    ExecutionEntity execution = createExecution();

    Map<String, Object> stageDefinition =
        Map.of(
            "id", "sql-stage",
            "type", "sql",
            "config", Map.of("query", "SELECT 1"));

    when(defaultDataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.execute("SELECT 1")).thenReturn(true);
    when(statement.getResultSet()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(1);
    when(resultSetMetaData.getColumnLabel(1)).thenReturn("?column?");
    when(resultSet.next()).thenReturn(false);

    EmbeddedStageExecutor.StageExecutionResult result =
        executor.execute(job, execution, stageDefinition);

    assertThat(result.exitCode()).isEqualTo(0);
    verify(defaultDataSource).getConnection();
  }

  private JobEntity createJob() throws Exception {
    JobEntity job =
        JobEntity.builder()
            .executionId(UUID.randomUUID())
            .stageId("sql-stage")
            .stageName("SQL Stage")
            .build();
    setId(job, UUID.randomUUID());
    return job;
  }

  private ExecutionEntity createExecution() throws Exception {
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
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }
}
