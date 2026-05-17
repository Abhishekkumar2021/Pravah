package io.pravah.common.domain.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StageOutputRefTest {

  @Test
  void validReference_simpleKey() {
    StageOutputRef ref = new StageOutputRef("extract", "row_count");

    assertThat(ref.stageId()).isEqualTo("extract");
    assertThat(ref.outputPath()).isEqualTo("row_count");
    assertThat(ref.raw()).isEqualTo("${stages.extract.output.row_count}");
    assertThat(ref.isDeferred()).isTrue();
    assertThat(ref.type()).isEqualTo(ValueReference.ReferenceType.STAGE_OUTPUT);
  }

  @Test
  void validReference_nestedPath() {
    StageOutputRef ref = new StageOutputRef("query", "preview.0.id");

    assertThat(ref.stageId()).isEqualTo("query");
    assertThat(ref.outputPath()).isEqualTo("preview.0.id");
    assertThat(ref.raw()).isEqualTo("${stages.query.output.preview.0.id}");
  }

  @Test
  void validReference_hyphenatedStageId() {
    StageOutputRef ref = new StageOutputRef("my-extract-stage", "count");

    assertThat(ref.stageId()).isEqualTo("my-extract-stage");
    assertThat(ref.outputPath()).isEqualTo("count");
  }

  @Test
  void validReference_underscoreStageId() {
    StageOutputRef ref = new StageOutputRef("_internal_stage", "data");

    assertThat(ref.stageId()).isEqualTo("_internal_stage");
    assertThat(ref.outputPath()).isEqualTo("data");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "123invalid", "has space", "has.dot"})
  void invalidStageId_throws(String stageId) {
    assertThatThrownBy(() -> new StageOutputRef(stageId, "key"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullStageId_throws() {
    assertThatThrownBy(() -> new StageOutputRef(null, "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Stage ID is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "123start", "has space", "has-dash"})
  void invalidOutputPath_throws(String path) {
    assertThatThrownBy(() -> new StageOutputRef("stage", path))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullOutputPath_throws() {
    assertThatThrownBy(() -> new StageOutputRef("stage", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Output path is required");
  }

  @Test
  void isDeferred_alwaysTrue() {
    StageOutputRef ref = new StageOutputRef("stage", "key");
    assertThat(ref.isDeferred()).isTrue();
  }
}
