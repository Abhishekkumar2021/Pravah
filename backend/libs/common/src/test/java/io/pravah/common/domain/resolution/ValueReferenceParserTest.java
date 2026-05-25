package io.pravah.common.domain.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueReferenceParserTest {

  @Test
  void extractAll_variableReferences() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("SELECT * FROM t WHERE x = ${var.batch_size}");
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0)).isInstanceOf(VariableRef.class);
    assertThat(((VariableRef) refs.get(0)).name()).isEqualTo("batch_size");
  }

  @Test
  void extractAll_secretReferences() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("Authorization: Bearer ${secret.api_key}");
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0)).isInstanceOf(SecretRef.class);
    assertThat(((SecretRef) refs.get(0)).name()).isEqualTo("api_key");
  }

  @Test
  void extractAll_builtinReferences() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("Date: ${execution_date}, ID: ${execution_id}");
    assertThat(refs).hasSize(2);
    assertThat(refs.get(0)).isInstanceOf(BuiltinRef.class);
    assertThat(((BuiltinRef) refs.get(0)).name()).isEqualTo("execution_date");
    assertThat(refs.get(1)).isInstanceOf(BuiltinRef.class);
    assertThat(((BuiltinRef) refs.get(1)).name()).isEqualTo("execution_id");
  }

  @Test
  void extractAll_mixedReferences() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll(
            "Batch ${var.size} on ${execution_date} with key ${secret.key}");
    assertThat(refs).hasSize(3);
    assertThat(refs.get(0)).isInstanceOf(VariableRef.class);
    assertThat(refs.get(1)).isInstanceOf(BuiltinRef.class);
    assertThat(refs.get(2)).isInstanceOf(SecretRef.class);
  }

  @Test
  void extractAll_noReferences_returnsEmptyList() {
    assertThat(ValueReferenceParser.extractAll("plain text")).isEmpty();
    assertThat(ValueReferenceParser.extractAll(null)).isEmpty();
    assertThat(ValueReferenceParser.extractAll("")).isEmpty();
  }

  @Test
  void extractVariableNames_returnsOnlyVariables() {
    List<String> names =
        ValueReferenceParser.extractVariableNames(
            "${var.a} and ${secret.b} and ${var.c} and ${execution_date}");
    assertThat(names).containsExactly("a", "c");
  }

  @Test
  void extractSecretNames_returnsOnlySecrets() {
    List<String> names =
        ValueReferenceParser.extractSecretNames(
            "${var.a} and ${secret.b} and ${secret.c} and ${execution_date}");
    assertThat(names).containsExactly("b", "c");
  }

  @ParameterizedTest
  @CsvSource({
    "env:MY_VAR,MY_VAR",
    "env:DB_PASSWORD,DB_PASSWORD",
    "env:A1_B2_C3,A1_B2_C3",
    "env:_PRIVATE,_PRIVATE"
  })
  void parseCredentialRef_envRefs(String input, String expectedVarName) {
    ValueReference ref = ValueReferenceParser.parseCredentialRef(input);
    assertThat(ref).isInstanceOf(EnvRef.class);
    assertThat(((EnvRef) ref).varName()).isEqualTo(expectedVarName);
  }

  @ParameterizedTest
  @CsvSource({
    "vault:secret/data/myapp#password,secret/data/myapp,password",
    "vault:kv/data/prod/db#api_key,kv/data/prod/db,api_key"
  })
  void parseCredentialRef_vaultRefs(String input, String expectedPath, String expectedKey) {
    ValueReference ref = ValueReferenceParser.parseCredentialRef(input);
    assertThat(ref).isInstanceOf(VaultRef.class);
    VaultRef vaultRef = (VaultRef) ref;
    assertThat(vaultRef.path()).isEqualTo(expectedPath);
    assertThat(vaultRef.key()).isEqualTo(expectedKey);
  }

  @Test
  void parseCredentialRef_secretRef() {
    ValueReference ref = ValueReferenceParser.parseCredentialRef("${secret.db_password}");
    assertThat(ref).isInstanceOf(SecretRef.class);
    assertThat(((SecretRef) ref).name()).isEqualTo("db_password");
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "foo:bar", "aws:something", "literal value"})
  void parseCredentialRef_invalidFormats_throws(String input) {
    assertThatThrownBy(() -> ValueReferenceParser.parseCredentialRef(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported credential reference format");
  }

  @ParameterizedTest
  @ValueSource(strings = {"env:", "env:   ", "env:123invalid", "env:has-dash"})
  void parseCredentialRef_invalidEnvVarNames_throws(String input) {
    assertThatThrownBy(() -> ValueReferenceParser.parseCredentialRef(input))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void containsDeferredRefs_trueForSecrets() {
    assertThat(ValueReferenceParser.containsDeferredRefs("${secret.api_key}")).isTrue();
    assertThat(ValueReferenceParser.containsDeferredRefs("env:MY_VAR")).isTrue();
    assertThat(ValueReferenceParser.containsDeferredRefs("vault:path#key")).isTrue();
  }

  @Test
  void containsDeferredRefs_falseForVariables() {
    assertThat(ValueReferenceParser.containsDeferredRefs("${var.name}")).isFalse();
    assertThat(ValueReferenceParser.containsDeferredRefs("${execution_date}")).isFalse();
    assertThat(ValueReferenceParser.containsDeferredRefs("plain text")).isFalse();
  }

  @Test
  void containsVariableRefs_trueForVariables() {
    assertThat(ValueReferenceParser.containsVariableRefs("${var.name}")).isTrue();
    assertThat(ValueReferenceParser.containsVariableRefs("prefix ${var.x} suffix")).isTrue();
  }

  @Test
  void containsVariableRefs_falseForOthers() {
    assertThat(ValueReferenceParser.containsVariableRefs("${secret.name}")).isFalse();
    assertThat(ValueReferenceParser.containsVariableRefs("${execution_date}")).isFalse();
  }

  @Test
  void extractAll_stageOutputReferences() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("Row count: ${stages.extract.output.row_count}");
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0)).isInstanceOf(StageOutputRef.class);
    StageOutputRef stageRef = (StageOutputRef) refs.get(0);
    assertThat(stageRef.stageId()).isEqualTo("extract");
    assertThat(stageRef.outputPath()).isEqualTo("row_count");
  }

  @Test
  void extractAll_stageOutputWithNestedPath() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("First row ID: ${stages.query.output.preview.0.id}");
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0)).isInstanceOf(StageOutputRef.class);
    StageOutputRef stageRef = (StageOutputRef) refs.get(0);
    assertThat(stageRef.stageId()).isEqualTo("query");
    assertThat(stageRef.outputPath()).isEqualTo("preview.0.id");
  }

  @Test
  void extractAll_stageOutputWithHyphenatedStageId() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll("${stages.my-extract-stage.output.count}");
    assertThat(refs).hasSize(1);
    StageOutputRef stageRef = (StageOutputRef) refs.get(0);
    assertThat(stageRef.stageId()).isEqualTo("my-extract-stage");
    assertThat(stageRef.outputPath()).isEqualTo("count");
  }

  @Test
  void extractAll_mixedWithStageOutput() {
    List<ValueReference> refs =
        ValueReferenceParser.extractAll(
            "Extracted ${stages.extract.output.row_count} rows at ${execution_date}");
    assertThat(refs).hasSize(2);
    assertThat(refs.get(0)).isInstanceOf(StageOutputRef.class);
    assertThat(refs.get(1)).isInstanceOf(BuiltinRef.class);
  }

  @Test
  void extractStageOutputRefs_returnsOnlyStageOutputs() {
    List<StageOutputRef> refs =
        ValueReferenceParser.extractStageOutputRefs(
            "${var.a} and ${stages.s1.output.x} and ${secret.b} and ${stages.s2.output.y}");
    assertThat(refs).hasSize(2);
    assertThat(refs.get(0).stageId()).isEqualTo("s1");
    assertThat(refs.get(0).outputPath()).isEqualTo("x");
    assertThat(refs.get(1).stageId()).isEqualTo("s2");
    assertThat(refs.get(1).outputPath()).isEqualTo("y");
  }

  @Test
  void containsDeferredRefs_trueForStageOutputs() {
    assertThat(ValueReferenceParser.containsDeferredRefs("${stages.extract.output.count}"))
        .isTrue();
  }
}
