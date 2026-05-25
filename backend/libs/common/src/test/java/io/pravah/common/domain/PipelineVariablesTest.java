package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineVariablesTest {

  @Test
  @SuppressWarnings("unchecked")
  void resolve_appliesEnvironmentThenRuntimeOverrides() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of(
                "env",
                Map.of("type", "string", "default", "dev"),
                "batch_size",
                Map.of("type", "number", "default", 100)),
            "environments",
            Map.of("prod", Map.of("env", "prod", "batch_size", 500)),
            "stages",
            List.of(
                Map.of(
                    "id",
                    "extract",
                    "config",
                    Map.of("query", "SELECT * FROM t WHERE env = '${var.env}'"))));

    UUID pipelineId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-16T12:00:00Z");

    Map<String, Object> resolved =
        PipelineDefinitionResolver.resolveForExecution(
            definition,
            Map.of(PipelineVariable.ENVIRONMENT_PARAMETER, "prod", "batch_size", 200),
            pipelineId,
            1,
            UUID.randomUUID(),
            now);

    List<Map<String, Object>> stages = (List<Map<String, Object>>) resolved.get("stages");
    Map<String, Object> config = (Map<String, Object>) stages.get(0).get("config");
    String query = config.get("query").toString();

    assertThat(query).isEqualTo("SELECT * FROM t WHERE env = 'prod'");
  }

  @Test
  void validateDefinition_rejectsUndefinedVarReference() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("known", Map.of("type", "string", "default", "x")),
            "stages",
            List.of(Map.of("config", Map.of("path", "${var.unknown}"))));

    assertThatThrownBy(() -> PipelineVariablesParser.validateDefinition(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("undefined variable");
  }

  @Test
  void resolve_requiredVariableWithoutDefault_throws() {
    Map<String, Object> definition =
        Map.of("variables", Map.of("region", Map.of("type", "string", "required", true)));

    assertThatThrownBy(
            () ->
                VariableContextResolver.resolve(
                    definition,
                    null,
                    Map.of(),
                    UUID.randomUUID(),
                    1,
                    UUID.randomUUID(),
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolve_ignoresUndeclaredRuntimeParameters() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("batch_size", Map.of("type", "number", "default", 100)),
            "stages",
            List.of(Map.of("id", "a")));

    Map<String, Object> context =
        VariableContextResolver.resolve(
            definition,
            null,
            Map.of("env", "prod", "batch_size", 200),
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            Instant.now());

    assertThat(context).containsEntry("batch_size", 200);
    assertThat(context).doesNotContainKey("env");
  }

  @Test
  void resolve_unknownEnvironment_throws() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("x", Map.of("type", "string", "default", "a")),
            "stages",
            List.of(Map.of("id", "a")));

    assertThatThrownBy(
            () ->
                VariableContextResolver.resolve(
                    definition,
                    "nonexistent",
                    Map.of(),
                    UUID.randomUUID(),
                    1,
                    UUID.randomUUID(),
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown environment");
  }

  @Test
  void coerceBoolean_rejectsInvalidString() {
    PipelineVariable variable =
        new PipelineVariable("flag", PipelineVariable.VariableType.BOOLEAN, null, false);

    assertThatThrownBy(() -> variable.coerceRuntimeValue("yes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be 'true' or 'false'");

    assertThatThrownBy(() -> variable.coerceRuntimeValue("tru"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void coerceBoolean_acceptsValidStrings() {
    PipelineVariable variable =
        new PipelineVariable("flag", PipelineVariable.VariableType.BOOLEAN, null, false);

    assertThat(variable.coerceRuntimeValue("true")).isEqualTo(true);
    assertThat(variable.coerceRuntimeValue("false")).isEqualTo(false);
    assertThat(variable.coerceRuntimeValue("TRUE")).isEqualTo(true);
    assertThat(variable.coerceRuntimeValue("False")).isEqualTo(false);
  }

  @Test
  void coerceNumber_fromStringSucceeds() {
    PipelineVariable variable =
        new PipelineVariable("count", PipelineVariable.VariableType.NUMBER, null, false);

    assertThat(variable.coerceRuntimeValue("123.45")).isEqualTo(123.45);
    assertThat(variable.coerceRuntimeValue("42")).isEqualTo(42.0);
  }

  @Test
  void coerceNumber_invalidStringThrows() {
    PipelineVariable variable =
        new PipelineVariable("count", PipelineVariable.VariableType.NUMBER, null, false);

    assertThatThrownBy(() -> variable.coerceRuntimeValue("abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a number");
  }

  @Test
  void default_substitutesExecutionDateBuiltin() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("run_date", Map.of("type", "string", "default", "${execution_date}")));

    Map<String, Object> context =
        VariableContextResolver.resolve(
            definition,
            null,
            Map.of(),
            UUID.randomUUID(),
            2,
            UUID.randomUUID(),
            Instant.parse("2026-05-16T15:30:00Z"));

    assertThat(context.get("run_date")).isEqualTo("2026-05-16");
  }
}
