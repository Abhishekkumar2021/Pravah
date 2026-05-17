package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

/**
 * Executes SQL stages against JDBC-compatible databases (US-02.14).
 *
 * <p>Stage config:
 *
 * <pre>
 * stages:
 *   - id: extract
 *     type: sql
 *     config:
 *       query: "SELECT * FROM orders WHERE status = 'pending'"
 *       # Named platform connection (preferred)
 *       connection: warehouse
 *       # Or inline JDBC (local overrides)
 *       connection:
 *         url: jdbc:postgresql://localhost:5432/warehouse
 *         username: user
 *         password: pass
 * </pre>
 *
 * <p>Output includes: row_count, duration_ms, columns (list of column names), and optionally first
 * N rows for downstream stages.
 *
 * <h2>Security Model</h2>
 *
 * <p><strong>SQL Injection:</strong> Pravah executes user-authored SQL queries as-is without
 * parameterization or sanitization. This is intentional — pipeline authors need full SQL
 * expressiveness. Security is enforced through:
 *
 * <ul>
 *   <li><strong>Connection-scoped credentials:</strong> Each connection uses dedicated database
 *       users with minimal required privileges (principle of least privilege)
 *   <li><strong>Tenant isolation:</strong> Connections are tenant-scoped; cross-tenant access is
 *       prevented at the platform level
 *   <li><strong>RBAC:</strong> Only authorized users can create/edit pipelines and connections
 *   <li><strong>Audit logging:</strong> All SQL executions are logged with job context
 * </ul>
 *
 * <p>Organizations should configure database users referenced by connections with appropriate
 * permissions (e.g., read-only for extract stages, write access for load stages).
 */
@Component
public class SqlEmbeddedStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(SqlEmbeddedStageExecutor.class);

  private static final int MAX_PREVIEW_ROWS = 10;
  private static final int QUERY_TIMEOUT_SECONDS = 300;

  private final JobLogService jobLogService;
  private final ConnectionCatalog connectionCatalog;
  private final DataSource defaultDataSource;
  private final ExecutionStageConfigResolver configResolver;

  public SqlEmbeddedStageExecutor(
      JobLogService jobLogService,
      ConnectionCatalog connectionCatalog,
      DataSource defaultDataSource,
      ExecutionStageConfigResolver configResolver) {
    this.jobLogService = jobLogService;
    this.connectionCatalog = connectionCatalog;
    this.defaultDataSource = defaultDataSource;
    this.configResolver = configResolver;
  }

  /**
   * Executes the SQL query defined in the stage config.
   *
   * @param job the job entity
   * @param execution the parent execution
   * @param stageConfig the stage configuration map (from definition snapshot)
   * @return execution result with exit code 0 on success, non-zero on failure
   */
  public EmbeddedStageExecutor.StageExecutionResult execute(
      JobEntity job, ExecutionEntity execution, Map<String, Object> stageDefinition) {

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "sql");
    output.put("stageId", job.getStageId());

    Map<String, Object> rawConfig = extractConfig(stageDefinition);
    if (rawConfig == null) {
      String error = "SQL stage missing required 'config' section";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    Map<String, Object> stageConfig;
    try {
      stageConfig = configResolver.resolveConfig(execution, rawConfig);
    } catch (RuntimeException e) {
      log.error(
          "SQL config resolution failed",
          kv("job_id", job.getId()),
          kv("stage_id", job.getStageId()),
          kv("execution_id", execution.getId()),
          e);
      String userError = "Failed to resolve SQL stage configuration. Check logs for details.";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[sql] " + userError);
      output.put("error", userError);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    String query = extractQuery(stageConfig);
    if (query == null || query.isBlank()) {
      String error = "SQL stage missing required 'query' in config";
      jobLogService.append(job.getId(), JobLogLevel.ERROR, error);
      output.put("error", error);
      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }

    jobLogService.append(
        job.getId(),
        JobLogLevel.INFO,
        "[sql] Executing query for stage %s".formatted(job.getStageId()));

    DataSource dataSource = resolveDataSource(stageConfig, execution);
    Instant startTime = Instant.now();

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

      boolean hasResultSet = stmt.execute(query);
      Duration duration = Duration.between(startTime, Instant.now());
      output.put("duration_ms", duration.toMillis());

      if (hasResultSet) {
        try (ResultSet rs = stmt.getResultSet()) {
          populateResultOutput(rs, output);
        }
      } else {
        int updateCount = stmt.getUpdateCount();
        output.put("row_count", updateCount);
        output.put("query_type", "UPDATE/INSERT/DELETE");
        jobLogService.append(
            job.getId(),
            JobLogLevel.INFO,
            "[sql] Query affected %d rows in %d ms".formatted(updateCount, duration.toMillis()));
      }

      return new EmbeddedStageExecutor.StageExecutionResult(0, output);

    } catch (SQLException e) {
      Duration duration = Duration.between(startTime, Instant.now());
      output.put("duration_ms", duration.toMillis());
      String userError = "SQL execution failed. Check logs for details.";
      output.put("error", userError);

      jobLogService.append(job.getId(), JobLogLevel.ERROR, "[sql] " + userError);
      log.warn(
          "SQL stage execution failed",
          kv("job_id", job.getId()),
          kv("stage_id", job.getStageId()),
          kv("execution_id", execution.getId()),
          kv("sql_state", e.getSQLState()),
          kv("error_code", e.getErrorCode()),
          e);

      return new EmbeddedStageExecutor.StageExecutionResult(1, output);
    }
  }

  private void populateResultOutput(ResultSet rs, Map<String, Object> output) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    List<String> columns = new ArrayList<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      columns.add(meta.getColumnLabel(i));
    }
    output.put("columns", columns);

    List<Map<String, Object>> preview = new ArrayList<>();
    int rowCount = 0;
    while (rs.next()) {
      rowCount++;
      if (preview.size() < MAX_PREVIEW_ROWS) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          row.put(columns.get(i - 1), rs.getObject(i));
        }
        preview.add(row);
      }
    }

    output.put("row_count", rowCount);
    output.put("preview", preview);
    output.put("query_type", "SELECT");
  }

  private DataSource resolveDataSource(Map<String, Object> stageConfig, ExecutionEntity execution) {
    Object connObj = stageConfig.get("connection");
    if (connObj instanceof String connectionName && !connectionName.isBlank()) {
      ResolvedJdbcConnection resolved =
          connectionCatalog.resolve(execution.getTenantId(), connectionName.trim());
      return DataSourceBuilder.create()
          .url(resolved.jdbcUrl())
          .username(resolved.username())
          .password(resolved.password())
          .build();
    }
    if (connObj instanceof Map<?, ?> connMap) {
      String url = getString(connMap, "url");
      String username = getString(connMap, "username");
      String password = getString(connMap, "password");

      if (url != null && !url.isBlank()) {
        return DataSourceBuilder.create()
            .url(url)
            .username(username != null ? username : "")
            .password(password != null ? password : "")
            .build();
      }
    }

    log.debug("No connection config in SQL stage; using default datasource");
    return defaultDataSource;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractConfig(Map<String, Object> stageDefinition) {
    if (stageDefinition == null) {
      return null;
    }
    Object configObj = stageDefinition.get("config");
    if (configObj instanceof Map<?, ?> config) {
      return (Map<String, Object>) config;
    }
    return null;
  }

  private String extractQuery(Map<String, Object> stageConfig) {
    Object queryObj = stageConfig.get("query");
    return queryObj != null ? queryObj.toString() : null;
  }

  private static String getString(Map<?, ?> map, String key) {
    Object v = map.get(key);
    return v != null ? v.toString() : null;
  }
}
