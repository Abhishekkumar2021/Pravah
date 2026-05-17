package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SparkStageValidatorTest {

  @Test
  void validMainClass_passes() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "spark_job",
                    "type",
                    "spark",
                    "config",
                    Map.of("main_class", "com.example.App"))));
    assertThatCode(() -> SparkStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void missingMainClassAndJar_fails() {
    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "spark_job", "type", "spark", "config", Map.of())));
    assertThatThrownBy(() -> SparkStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("main_class");
  }
}
