package io.pravah.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.runner.RemoteJobSpecEnv;
import io.pravah.proto.runner.JobAssignment;
import io.pravah.proto.runner.JobSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobExecutorTest {

  @TempDir Path workDir;

  @Test
  void execute_shell_runsCommand() throws Exception {
    JobAssignment assignment =
        JobAssignment.newBuilder()
            .setJobId("job-1")
            .setSpec(
                JobSpec.newBuilder()
                    .setExecutor("shell")
                    .addAllCommands(List.of("sh", "-c", "exit 0"))
                    .setTimeoutSeconds(30)
                    .build())
            .build();

    JobExecutionResult result = new JobExecutor(workDir.toString()).execute(assignment);

    assertThat(result.exitCode()).isZero();
  }

  @Test
  void execute_python_writesScriptFromEnv() throws Exception {
    String script = "import json\nprint(json.dumps({'ok': True}))";
    JobAssignment assignment =
        JobAssignment.newBuilder()
            .setJobId("job-2")
            .setSpec(
                JobSpec.newBuilder()
                    .setExecutor("python")
                    .putEnvironment(RemoteJobSpecEnv.PYTHON_SCRIPT, script)
                    .setTimeoutSeconds(60)
                    .build())
            .build();

    JobExecutionResult result = new JobExecutor(workDir.toString()).execute(assignment);

    assertThat(result.exitCode()).isZero();
    assertThat(Files.exists(workDir.resolve("stage_script.py"))).isTrue();
  }
}
