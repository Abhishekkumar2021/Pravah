package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PythonStageValidatorTest {

  @Test
  void validScript_passes() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "run_py",
                    "type",
                    "python",
                    "config",
                    Map.of("script", "print('hello')"))));
    assertThatCode(() -> PythonStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  void missingScriptAndCommand_fails() {
    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "run_py", "type", "python", "config", Map.of())));
    assertThatThrownBy(() -> PythonStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("config.script");
  }
}
