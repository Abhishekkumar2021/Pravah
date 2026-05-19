package io.pravah.runner;

import io.pravah.proto.runner.JobAssignment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes assigned jobs locally (shell/Docker). Supports DuckDB via Python sidecar script. */
public class JobExecutor {

  private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);
  private final Path workDir;

  public JobExecutor(String workDir) {
    this.workDir = Path.of(workDir);
  }

  public int execute(JobAssignment assignment) throws Exception {
    Files.createDirectories(workDir);
    String executor = assignment.getSpec().getExecutor();

    if ("python".equalsIgnoreCase(executor)) {
      return runPython(assignment);
    }
    if ("docker".equalsIgnoreCase(executor) || "container".equalsIgnoreCase(executor)) {
      return runDocker(assignment);
    }
    return runShell(assignment);
  }

  private int runShell(JobAssignment assignment) throws Exception {
    List<String> command = new ArrayList<>();
    if (assignment.getSpec().getCommandsCount() > 0) {
      command.addAll(assignment.getSpec().getCommandsList());
    } else {
      command.add("echo");
      command.add("runner job " + assignment.getJobId());
    }
    return runProcess(command, assignment.getSpec().getEnvironmentMap(), 3600);
  }

  private int runDocker(JobAssignment assignment) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("docker");
    command.add("run");
    command.add("--rm");
    String image = assignment.getSpec().getImage();
    if (image == null || image.isBlank()) {
      image = "alpine:3.19";
    }
    command.add(image);
    if (assignment.getSpec().getCommandsCount() > 0) {
      command.addAll(assignment.getSpec().getCommandsList());
    }
    return runProcess(
        command,
        assignment.getSpec().getEnvironmentMap(),
        assignment.getSpec().getTimeoutSeconds());
  }

  /** Runs DuckDB SQL transform when job environment contains PRAVAH_DUCKDB_SQL. */
  private int runPython(JobAssignment assignment) throws Exception {
    Map<String, String> env = assignment.getSpec().getEnvironmentMap();
    String duckdbSql = env.get("PRAVAH_DUCKDB_SQL");
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
      return runProcess(command, env, assignment.getSpec().getTimeoutSeconds());
    }
    return runShell(assignment);
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
