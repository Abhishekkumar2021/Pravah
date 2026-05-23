package io.pravah.runner;

import io.pravah.common.runner.RemoteJobSpecEnv;
import io.pravah.proto.runner.JobAssignment;
import io.pravah.proto.runner.JobSpec;
import io.pravah.proto.runner.ResourceRequirements;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes assigned jobs locally (shell/Docker/SQL/Python). */
public class JobExecutor {

  private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);
  private final Path workDir;

  public JobExecutor(String workDir) {
    this.workDir = Path.of(workDir);
  }

  public JobExecutionResult execute(JobAssignment assignment) throws Exception {
    Files.createDirectories(workDir);
    String executor = assignment.getSpec().getExecutor();

    if ("python".equalsIgnoreCase(executor)) {
      return runPython(assignment);
    }
    if ("sql".equalsIgnoreCase(executor)) {
      return runSql(assignment);
    }
    if ("docker".equalsIgnoreCase(executor) || "container".equalsIgnoreCase(executor)) {
      return runDocker(assignment);
    }
    int exitCode = runShell(assignment);
    return exitCode == 0
        ? JobExecutionResult.success(Map.of())
        : JobExecutionResult.failure(exitCode);
  }

  private int runShell(JobAssignment assignment) throws Exception {
    List<String> command = new ArrayList<>();
    if (assignment.getSpec().getCommandsCount() > 0) {
      command.addAll(assignment.getSpec().getCommandsList());
    } else {
      command.add("echo");
      command.add("runner job " + assignment.getJobId());
    }
    return runProcess(
        command, assignment.getSpec().getEnvironmentMap(), timeoutSeconds(assignment.getSpec()));
  }

  private JobExecutionResult runDocker(JobAssignment assignment) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("docker");
    command.add("run");
    command.add("--rm");
    applyResourceLimits(command, assignment.getSpec());
    String image = assignment.getSpec().getImage();
    if (image == null || image.isBlank()) {
      image = "alpine:3.19";
    }
    command.add(image);
    if (assignment.getSpec().getCommandsCount() > 0) {
      command.addAll(assignment.getSpec().getCommandsList());
    }
    int exitCode =
        runProcess(
            command,
            assignment.getSpec().getEnvironmentMap(),
            timeoutSeconds(assignment.getSpec()));
    return exitCode == 0
        ? JobExecutionResult.success(Map.of())
        : JobExecutionResult.failure(exitCode);
  }

  private JobExecutionResult runPython(JobAssignment assignment) throws Exception {
    Map<String, String> env = assignment.getSpec().getEnvironmentMap();
    String duckdbSql = env.get(RemoteJobSpecEnv.DUCKDB_SQL);
    if (duckdbSql != null && !duckdbSql.isBlank()) {
      Path script = workDir.resolve("duckdb_transform.py");
      String py =
          """
          import duckdb
          import json
          import os
          sql = os.environ['PRAVAH_DUCKDB_SQL']
          con = duckdb.connect()
          result = con.execute(sql).fetchall()
          print(json.dumps({"rows": len(result)}))
          """;
      Files.writeString(script, py);
      List<String> command = List.of("python3", script.toString());
      int exitCode = runProcess(command, env, timeoutSeconds(assignment.getSpec()));
      return exitCode == 0
          ? JobExecutionResult.success(Map.of())
          : JobExecutionResult.failure(exitCode);
    }

    String scriptBody = env.get(RemoteJobSpecEnv.PYTHON_SCRIPT);
    if (scriptBody == null || scriptBody.isBlank()) {
      int exitCode = runShell(assignment);
      return exitCode == 0
          ? JobExecutionResult.success(Map.of())
          : JobExecutionResult.failure(exitCode);
    }

    Path scriptPath = workDir.resolve("stage_script.py");
    Files.writeString(scriptPath, scriptBody);
    String requirements = env.get(RemoteJobSpecEnv.PYTHON_REQUIREMENTS);
    String requirementsFile = env.get(RemoteJobSpecEnv.PYTHON_REQUIREMENTS_FILE);
    if ((requirements == null || requirements.isBlank())
        && requirementsFile != null
        && !requirementsFile.isBlank()) {
      Path path = Path.of(requirementsFile);
      if (!path.isAbsolute()) {
        path = workDir.resolve(requirementsFile);
      }
      if (Files.exists(path)) {
        requirements = Files.readString(path, StandardCharsets.UTF_8);
      }
    }
    if (requirements != null && !requirements.isBlank()) {
      Path reqFile = workDir.resolve("requirements.txt");
      Files.writeString(reqFile, requirements);
      int pipExit =
          runProcess(
              List.of("python3", "-m", "pip", "install", "-q", "-r", reqFile.toString()),
              env,
              timeoutSeconds(assignment.getSpec()));
      if (pipExit != 0) {
        return JobExecutionResult.failure(pipExit);
      }
    }
    int exitCode =
        runProcess(
            List.of("python3", scriptPath.toString()), env, timeoutSeconds(assignment.getSpec()));
    return exitCode == 0
        ? JobExecutionResult.success(Map.of())
        : JobExecutionResult.failure(exitCode);
  }

  private JobExecutionResult runSql(JobAssignment assignment) throws Exception {
    Map<String, String> env = assignment.getSpec().getEnvironmentMap();
    String jdbcUrl = env.get(RemoteJobSpecEnv.SQL_JDBC_URL);
    String query = env.get(RemoteJobSpecEnv.SQL_QUERY);
    if (jdbcUrl == null || jdbcUrl.isBlank() || query == null || query.isBlank()) {
      throw new IllegalArgumentException("SQL job missing JDBC URL or query in environment");
    }
    String user = env.getOrDefault(RemoteJobSpecEnv.SQL_USER, "");
    String password = env.getOrDefault(RemoteJobSpecEnv.SQL_PASSWORD, "");

    try (Connection connection =
        DriverManager.getConnection(jdbcUrl, user.isBlank() ? null : user, password)) {
      try (Statement statement = connection.createStatement()) {
        boolean hasResultSet = statement.execute(query);
        if (!hasResultSet) {
          return JobExecutionResult.success(Map.of("update_count", statement.getUpdateCount()));
        }
        try (ResultSet rs = statement.getResultSet()) {
          return JobExecutionResult.success(serializeResultSet(rs));
        }
      }
    }
  }

  private static Map<String, Object> serializeResultSet(ResultSet rs) throws Exception {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();
    List<String> columns = new ArrayList<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      columns.add(meta.getColumnLabel(i));
    }
    List<List<Object>> rows = new ArrayList<>();
    int rowCount = 0;
    while (rs.next()) {
      List<Object> row = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        row.add(rs.getObject(i));
      }
      rows.add(row);
      rowCount++;
    }
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("columns", columns);
    output.put("rows", rows);
    output.put("row_count", rowCount);
    return output;
  }

  private static void applyResourceLimits(List<String> command, JobSpec spec) {
    if (!spec.hasResources()) {
      return;
    }
    ResourceRequirements resources = spec.getResources();
    if (resources.getMemoryBytes() > 0) {
      command.add("--memory");
      command.add(String.valueOf(resources.getMemoryBytes()));
    }
    if (resources.getCpuCores() > 0) {
      command.add("--cpus");
      command.add(String.valueOf(resources.getCpuCores()));
    }
  }

  private static long timeoutSeconds(JobSpec spec) {
    return spec.getTimeoutSeconds() > 0 ? spec.getTimeoutSeconds() : 3600;
  }

  private int runProcess(List<String> command, Map<String, String> env, long timeoutSeconds)
      throws Exception {
    log.info("Executing: {}", command);
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workDir.toFile());
    pb.redirectErrorStream(true);
    if (env != null) {
      pb.environment().putAll(env);
    }
    long timeout = timeoutSeconds > 0 ? timeoutSeconds : 3600;
    Process process = pb.start();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        log.info("[job] {}", line);
      }
    }
    if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("Process timed out after " + timeout + "s");
    }
    return process.exitValue();
  }
}
