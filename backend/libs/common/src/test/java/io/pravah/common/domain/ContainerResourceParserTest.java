package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerResourceParser")
class ContainerResourceParserTest {

  @Test
  void toDockerMemoryLimit_mebibytes() {
    assertThat(ContainerResourceParser.toDockerMemoryLimit("512Mi")).isEqualTo("512m");
    assertThat(ContainerResourceParser.toDockerMemoryLimit("1Gi")).isEqualTo("1g");
  }

  @Test
  void toDockerMemoryLimit_bytes() {
    assertThat(ContainerResourceParser.toDockerMemoryLimit("1048576")).isEqualTo("1048576");
  }

  @Test
  void toDockerMemoryLimit_invalid_throws() {
    assertThatThrownBy(() -> ContainerResourceParser.toDockerMemoryLimit("lots"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toDockerCpuLimit_valid() {
    assertThat(ContainerResourceParser.toDockerCpuLimit("0.5")).isEqualTo("0.5");
    assertThat(ContainerResourceParser.toDockerCpuLimit("2")).isEqualTo("2");
  }

  @Test
  void toDockerCpuLimit_zero_throws() {
    assertThatThrownBy(() -> ContainerResourceParser.toDockerCpuLimit("0"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
