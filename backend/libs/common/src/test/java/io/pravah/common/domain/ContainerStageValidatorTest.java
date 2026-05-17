package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerStageValidator")
class ContainerStageValidatorTest {

  @Test
  void validateDefinition_nullDefinition_doesNotThrow() {
    assertThatCode(() -> ContainerStageValidator.validateDefinition(null))
        .doesNotThrowAnyException();
  }

  @Test
  void validateDefinition_noStages_doesNotThrow() {
    assertThatCode(() -> ContainerStageValidator.validateDefinition(Map.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void validateDefinition_validContainerStage_doesNotThrow() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_tool",
                    "type",
                    "container",
                    "config",
                    Map.of(
                        "image",
                        "alpine:3.19",
                        "command",
                        List.of("echo", "hello"),
                        "env",
                        Map.of("FOO", "bar"),
                        "resources",
                        Map.of("memory", "512Mi", "cpus", "0.5")))));

    assertThatCode(() -> ContainerStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void validateDefinition_missingImage_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "run_tool", "type", "container", "config", Map.of("command", "echo"))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("image");
  }

  @Test
  void validateDefinition_blankImage_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "run_tool", "type", "container", "config", Map.of("image", "   "))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("image");
  }

  @Test
  void validateDefinition_invalidCommandType_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_tool",
                    "type",
                    "container",
                    "config",
                    Map.of("image", "alpine:3.19", "command", 42))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command");
  }

  @Test
  void validateDefinition_disallowedPrivilegedKey_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_tool",
                    "type",
                    "container",
                    "config",
                    Map.of("image", "alpine:3.19", "privileged", true))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("privileged");
  }

  @Test
  void validateDefinition_invalidImageFormat_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_tool",
                    "type",
                    "container",
                    "config",
                    Map.of("image", "alpine; rm -rf /"))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("image");
  }

  @Test
  void validateDefinition_invalidResourcesCpus_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_tool",
                    "type",
                    "container",
                    "config",
                    Map.of("image", "alpine:3.19", "resources", Map.of("cpus", "not-a-number")))));

    assertThatThrownBy(() -> ContainerStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cpus");
  }

  @Test
  void validateDefinition_nonContainerStages_ignored() {
    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "echo", "type", "echo")));

    assertThatCode(() -> ContainerStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }
}
