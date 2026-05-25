package io.pravah.runnerservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.proto.runner.JobSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobSpecMapperTest {

  @Test
  void toProto_mapsExecutorCommandsEnvAndResources() {
    RemoteJobSpecPayload payload =
        new RemoteJobSpecPayload(
            "container",
            "alpine:3.19",
            List.of("echo", "hi"),
            Map.of("FOO", "bar"),
            120L,
            536_870_912L,
            0.5);

    JobSpec spec = JobSpecMapper.toProto(payload);

    assertThat(spec.getExecutor()).isEqualTo("container");
    assertThat(spec.getImage()).isEqualTo("alpine:3.19");
    assertThat(spec.getCommandsList()).containsExactly("echo", "hi");
    assertThat(spec.getEnvironmentMap()).containsEntry("FOO", "bar");
    assertThat(spec.getTimeoutSeconds()).isEqualTo(120L);
    assertThat(spec.getResources().getMemoryBytes()).isEqualTo(536_870_912L);
    assertThat(spec.getResources().getCpuCores()).isEqualTo(0.5);
  }
}
