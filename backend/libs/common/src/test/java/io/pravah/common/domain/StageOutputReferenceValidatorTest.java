package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageOutputReferenceValidatorTest {

  @Test
  void validUpstreamReference_passes() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "type", "sql", "config", Map.of("query", "SELECT 1")),
                Map.of(
                    "id",
                    "notify",
                    "dependsOn",
                    List.of("extract"),
                    "config",
                    Map.of("message", "Rows: ${stages.extract.output.row_count}"))));

    assertThatCode(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void dependsOnSnakeCase_supported() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "config", Map.of()),
                Map.of(
                    "id",
                    "notify",
                    "depends_on",
                    List.of("extract"),
                    "config",
                    Map.of("message", "${stages.extract.output.row_count}"))));

    assertThatCode(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void transitiveUpstreamReference_passes() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "s1", "config", Map.of()),
                Map.of("id", "s2", "dependsOn", List.of("s1"), "config", Map.of()),
                Map.of(
                    "id",
                    "s3",
                    "dependsOn",
                    List.of("s2"),
                    "config",
                    Map.of("message", "${stages.s1.output.row_count}"))));

    assertThatCode(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void unknownStage_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "notify",
                    "config",
                    Map.of("message", "${stages.missing.output.row_count}"))));

    assertThatThrownBy(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown stage 'missing'");
  }

  @Test
  void nonUpstreamStage_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "config", Map.of()),
                Map.of("id", "parallel", "config", Map.of()),
                Map.of(
                    "id",
                    "notify",
                    "dependsOn",
                    List.of("extract"),
                    "config",
                    Map.of("message", "${stages.parallel.output.row_count}"))));

    assertThatThrownBy(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an upstream dependency");
  }

  @Test
  void selfReference_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "extract",
                    "config",
                    Map.of("message", "${stages.extract.output.row_count}"))));

    assertThatThrownBy(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot reference its own output");
  }

  @Test
  void unknownDependsOnTarget_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(Map.of("id", "notify", "dependsOn", List.of("missing"), "config", Map.of())));

    assertThatThrownBy(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("depends on unknown stage 'missing'");
  }

  @Test
  void stageOutputRefOutsideConfig_validated() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "config", Map.of("query", "SELECT 1")),
                Map.of(
                    "id",
                    "notify",
                    "dependsOn",
                    List.of("extract"),
                    "message",
                    "Rows: ${stages.extract.output.row_count}")));

    assertThatCode(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void circularDependency_throws() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "a", "dependsOn", List.of("b"), "config", Map.of()),
                Map.of("id", "b", "dependsOn", List.of("a"), "config", Map.of())));

    assertThatThrownBy(() -> StageOutputReferenceValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Circular stage dependency");
  }
}
