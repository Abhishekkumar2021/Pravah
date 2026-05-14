package io.pravah.pipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PipelineStateTest {

  @Nested
  class CanTransitionTo {

    @Test
    void draftMayPublishToActive() {
      assertThat(PipelineState.DRAFT.canTransitionTo(PipelineState.ACTIVE)).isTrue();
      assertThat(PipelineState.DRAFT.canTransitionTo(PipelineState.ARCHIVED)).isFalse();
      assertThat(PipelineState.DRAFT.canTransitionTo(PipelineState.DRAFT)).isFalse();
    }

    @Test
    void activeMayRepublishOrArchive() {
      assertThat(PipelineState.ACTIVE.canTransitionTo(PipelineState.ACTIVE)).isTrue();
      assertThat(PipelineState.ACTIVE.canTransitionTo(PipelineState.ARCHIVED)).isTrue();
      assertThat(PipelineState.ACTIVE.canTransitionTo(PipelineState.DRAFT)).isFalse();
    }

    @Test
    void archivedMayRestoreToActive() {
      assertThat(PipelineState.ARCHIVED.canTransitionTo(PipelineState.ACTIVE)).isTrue();
      assertThat(PipelineState.ARCHIVED.canTransitionTo(PipelineState.DRAFT)).isFalse();
      assertThat(PipelineState.ARCHIVED.canTransitionTo(PipelineState.ARCHIVED)).isFalse();
    }
  }

  @Nested
  class OnPublish {

    @Test
    void draft_transitionsToActive() {
      assertThat(PipelineState.DRAFT.onPublish()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void active_staysActive() {
      assertThat(PipelineState.ACTIVE.onPublish()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void archived_throws() {
      assertThatThrownBy(() -> PipelineState.ARCHIVED.onPublish())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("publish")
          .hasMessageContaining("ARCHIVED");
    }
  }

  @Nested
  class OnArchive {

    @Test
    void active_transitionsToArchived() {
      assertThat(PipelineState.ACTIVE.onArchive()).isEqualTo(PipelineState.ARCHIVED);
    }

    @Test
    void draft_throws() {
      assertThatThrownBy(() -> PipelineState.DRAFT.onArchive())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("archive")
          .hasMessageContaining("DRAFT");
    }

    @Test
    void archived_throws() {
      assertThatThrownBy(() -> PipelineState.ARCHIVED.onArchive())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("archive")
          .hasMessageContaining("ARCHIVED");
    }
  }

  @Nested
  class OnRestore {

    @Test
    void archived_transitionsToActive() {
      assertThat(PipelineState.ARCHIVED.onRestore()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void draft_throws() {
      assertThatThrownBy(() -> PipelineState.DRAFT.onRestore())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("restore")
          .hasMessageContaining("DRAFT");
    }

    @Test
    void active_throws() {
      assertThatThrownBy(() -> PipelineState.ACTIVE.onRestore())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("restore")
          .hasMessageContaining("ACTIVE");
    }
  }

  @Nested
  class DatabaseConversion {

    @ParameterizedTest
    @EnumSource(PipelineState.class)
    void databaseRoundTripForAllStates(PipelineState state) {
      String dbValue = state.asDatabaseValue();
      PipelineState restored = PipelineState.fromDatabase(dbValue);
      assertThat(restored).isEqualTo(state);
      assertThat(dbValue).isEqualTo(state.name().toLowerCase());
    }

    @Test
    void fromDatabaseIsCaseInsensitive() {
      assertThat(PipelineState.fromDatabase("DRAFT")).isEqualTo(PipelineState.DRAFT);
      assertThat(PipelineState.fromDatabase("Draft")).isEqualTo(PipelineState.DRAFT);
      assertThat(PipelineState.fromDatabase("active")).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void fromDatabaseWithInvalidValueThrows() {
      assertThatThrownBy(() -> PipelineState.fromDatabase("invalid"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDatabaseWithEmptyStringThrows() {
      assertThatThrownBy(() -> PipelineState.fromDatabase(""))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDatabaseWithBlankStringThrows() {
      assertThatThrownBy(() -> PipelineState.fromDatabase("   "))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDatabaseWithNullThrows() {
      assertThatThrownBy(() -> PipelineState.fromDatabase(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }
}
