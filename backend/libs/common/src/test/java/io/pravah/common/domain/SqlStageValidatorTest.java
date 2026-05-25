package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SqlStageValidator")
class SqlStageValidatorTest {

  @Test
  @DisplayName("null definition passes validation")
  void nullDefinition_passesValidation() {
    assertThatCode(() -> SqlStageValidator.validateDefinition(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("empty stages passes validation")
  void emptyStages_passesValidation() {
    Map<String, Object> definition = Map.of("stages", List.of());
    assertThatCode(() -> SqlStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("non-SQL stage with no type passes validation")
  void nonSqlStageNoType_passesValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "echo-stage",
                    "name", "Echo Stage")));
    assertThatCode(() -> SqlStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("valid SQL stage passes validation")
  void validSqlStage_passesValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "name", "Extract Data",
                    "type", "sql",
                    "config",
                        Map.of(
                            "query",
                            "SELECT * FROM orders",
                            "connection",
                            Map.of(
                                "url", "jdbc:postgresql://localhost/db",
                                "username", "user",
                                "password", "pass")))));
    assertThatCode(() -> SqlStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("SQL stage without connection passes validation (uses default)")
  void sqlStageWithoutConnection_passesValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1"))));
    assertThatCode(() -> SqlStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("SQL stage missing config fails validation")
  void sqlStageMissingConfig_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql")));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing required 'config' section");
  }

  @Test
  @DisplayName("SQL stage missing query fails validation")
  void sqlStageMissingQuery_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config",
                        Map.of("connection", Map.of("url", "jdbc:postgresql://localhost/db")))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing required 'query'");
  }

  @Test
  @DisplayName("SQL stage with blank query fails validation")
  void sqlStageBlankQuery_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config", Map.of("query", "   "))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing required 'query'");
  }

  @Test
  @DisplayName("SQL stage named connection reference passes validation")
  void sqlStageNamedConnection_passesValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1", "connection", "warehouse"))));
    assertThatCode(() -> SqlStageValidator.validateDefinition(definition))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("SQL stage connection missing URL fails validation")
  void sqlStageConnectionMissingUrl_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config",
                        Map.of("query", "SELECT 1", "connection", Map.of("username", "user")))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connection is missing required 'url'");
  }

  @Test
  @DisplayName("SQL stage connection invalid URL prefix fails validation")
  void sqlStageConnectionInvalidUrlPrefix_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract",
                    "type", "sql",
                    "config",
                        Map.of(
                            "query",
                            "SELECT 1",
                            "connection",
                            Map.of("url", "postgresql://localhost/db")))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must start with 'jdbc:'");
  }

  @Test
  @DisplayName("stage missing id fails validation")
  void stageMissingId_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "name", "Extract",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1"))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing required 'id' field");
  }

  @Test
  @DisplayName("duplicate stage ids fails validation")
  void duplicateStageIds_failsValidation() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract 1"),
                Map.of("id", "extract", "name", "Extract 2")));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate stage id: 'extract'");
  }

  @Test
  @DisplayName("multiple SQL stages all validated")
  void multipleSqlStages_allValidated() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "extract1",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1")),
                Map.of(
                    "id", "extract2",
                    "type", "sql",
                    "config", Map.of("query", ""))));
    assertThatThrownBy(() -> SqlStageValidator.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extract2")
        .hasMessageContaining("missing required 'query'");
  }
}
