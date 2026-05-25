package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbtStageValidatorTest {

  @Test
  void validSelect_passes() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "run_dbt", "type", "dbt", "config", Map.of("select", "tag:daily"))));
    assertThatCode(() -> DbtStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void missingModelsAndSelect_fails() {
    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "run_dbt", "type", "dbt", "config", Map.of())));
    assertThatThrownBy(() -> DbtStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("config.models");
  }
}
