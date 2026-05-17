package io.pravah.common.domain.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnifiedValueResolverTest {

  private UnifiedValueResolver resolver;
  private UUID tenantId;
  private UUID executionId;
  private Instant executionTime;

  @BeforeEach
  void setUp() {
    resolver =
        new UnifiedValueResolver(
            List.of(
                new VariableResolverProvider(),
                new BuiltinResolverProvider(),
                new EnvResolverProvider()));
    tenantId = UUID.randomUUID();
    executionId = UUID.randomUUID();
    executionTime = Instant.parse("2026-05-17T10:00:00Z");
  }

  @Test
  void resolve_literalString_returnsUnchanged() {
    ResolutionContext ctx = createContext(Map.of());
    Object result = resolver.resolve("plain text", ctx);
    assertThat(result).isEqualTo("plain text");
  }

  @Test
  void resolve_variableRef_substitutesValue() {
    ResolutionContext ctx = createContext(Map.of("batch_size", 100));
    Object result = resolver.resolve("Batch size: ${var.batch_size}", ctx);
    assertThat(result).isEqualTo("Batch size: 100");
  }

  @Test
  void resolve_multipleVariables_substitutesAll() {
    ResolutionContext ctx = createContext(Map.of("a", "X", "b", "Y"));
    Object result = resolver.resolve("${var.a} and ${var.b}", ctx);
    assertThat(result).isEqualTo("X and Y");
  }

  @Test
  void resolve_builtinRef_substitutesExecutionDate() {
    ResolutionContext ctx = createContext(Map.of());
    Object result = resolver.resolve("Date: ${execution_date}", ctx);
    assertThat(result).isEqualTo("Date: 2026-05-17");
  }

  @Test
  void resolve_builtinRef_substitutesExecutionId() {
    ResolutionContext ctx = createContext(Map.of());
    Object result = resolver.resolve("ID: ${execution_id}", ctx);
    assertThat(result).isEqualTo("ID: " + executionId);
  }

  @Test
  void resolve_undefinedVariable_throws() {
    ResolutionContext ctx = createContext(Map.of());
    assertThatThrownBy(() -> resolver.resolve("${var.undefined}", ctx))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Undefined variable");
  }

  @Test
  void resolveConfig_deeplyNestedMap() {
    Map<String, Object> config =
        Map.of(
            "level1",
            Map.of("level2", Map.of("value", "${var.x}")),
            "array",
            List.of("${var.y}", "literal"));
    ResolutionContext ctx = createContext(Map.of("x", "resolved_x", "y", "resolved_y"));

    Map<String, Object> result = resolver.resolveConfig(config, ctx);

    @SuppressWarnings("unchecked")
    Map<String, Object> level1 = (Map<String, Object>) result.get("level1");
    @SuppressWarnings("unchecked")
    Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
    assertThat(level2.get("value")).isEqualTo("resolved_x");

    @SuppressWarnings("unchecked")
    List<Object> array = (List<Object>) result.get("array");
    assertThat(array).containsExactly("resolved_y", "literal");
  }

  @Test
  void resolveConfig_nullMap_returnsEmptyMap() {
    ResolutionContext ctx = createContext(Map.of());
    Map<String, Object> result = resolver.resolveConfig(null, ctx);
    assertThat(result).isEmpty();
  }

  @Test
  void resolve_nullValue_returnsNull() {
    ResolutionContext ctx = createContext(Map.of());
    assertThat(resolver.resolve(null, ctx)).isNull();
  }

  @Test
  void resolve_nonStringPrimitives_returnUnchanged() {
    ResolutionContext ctx = createContext(Map.of());
    assertThat(resolver.resolve(42, ctx)).isEqualTo(42);
    assertThat(resolver.resolve(true, ctx)).isEqualTo(true);
    assertThat(resolver.resolve(3.14, ctx)).isEqualTo(3.14);
  }

  private ResolutionContext createContext(Map<String, Object> variables) {
    Map<String, Object> fullContext = new java.util.HashMap<>(variables);
    fullContext.put("__pipeline_id", UUID.randomUUID().toString());
    fullContext.put("__pipeline_version", "1");
    return ResolutionContext.forExecution(tenantId, executionId, executionTime, fullContext);
  }
}
