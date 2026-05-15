package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JobState")
class JobStateTest {

  @Nested
  @DisplayName("State transitions")
  class StateTransitions {

    @Test
    @DisplayName("PENDING → QUEUED on dependencies met")
    void pending_onDependenciesMet_transitionsToQueued() {
      assertThat(JobState.PENDING.onDependenciesMet()).isEqualTo(JobState.QUEUED);
    }

    @Test
    @DisplayName("PENDING → SKIPPED on skip")
    void pending_onSkip_transitionsToSkipped() {
      assertThat(JobState.PENDING.onSkip()).isEqualTo(JobState.SKIPPED);
    }

    @Test
    @DisplayName("PENDING → CANCELLED on cancel")
    void pending_onCancel_transitionsToCancelled() {
      assertThat(JobState.PENDING.onCancel()).isEqualTo(JobState.CANCELLED);
    }

    @Test
    @DisplayName("QUEUED → RUNNING on assign")
    void queued_onAssign_transitionsToRunning() {
      assertThat(JobState.QUEUED.onAssign()).isEqualTo(JobState.RUNNING);
    }

    @Test
    @DisplayName("QUEUED → CANCELLED on cancel")
    void queued_onCancel_transitionsToCancelled() {
      assertThat(JobState.QUEUED.onCancel()).isEqualTo(JobState.CANCELLED);
    }

    @Test
    @DisplayName("RUNNING → SUCCEEDED on success")
    void running_onSuccess_transitionsToSucceeded() {
      assertThat(JobState.RUNNING.onSuccess()).isEqualTo(JobState.SUCCEEDED);
    }

    @Test
    @DisplayName("RUNNING → QUEUED on failure with retry")
    void running_onFailureWithRetry_transitionsToQueued() {
      assertThat(JobState.RUNNING.onFailure(true)).isEqualTo(JobState.QUEUED);
    }

    @Test
    @DisplayName("RUNNING → FAILED on failure without retry")
    void running_onFailureNoRetry_transitionsToFailed() {
      assertThat(JobState.RUNNING.onFailure(false)).isEqualTo(JobState.FAILED);
    }

    @Test
    @DisplayName("RUNNING → CANCELLED on cancel")
    void running_onCancel_transitionsToCancelled() {
      assertThat(JobState.RUNNING.onCancel()).isEqualTo(JobState.CANCELLED);
    }
  }

  @Nested
  @DisplayName("Invalid transitions")
  class InvalidTransitions {

    @Test
    @DisplayName("PENDING cannot be assigned")
    void pending_onAssign_throws() {
      assertThatThrownBy(() -> JobState.PENDING.onAssign())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot assign job in state PENDING");
    }

    @Test
    @DisplayName("QUEUED cannot succeed")
    void queued_onSuccess_throws() {
      assertThatThrownBy(() -> JobState.QUEUED.onSuccess())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SUCCEEDED cannot transition")
    void succeeded_anyTransition_throws() {
      assertThatThrownBy(() -> JobState.SUCCEEDED.onCancel())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FAILED cannot retry")
    void failed_onFailure_throws() {
      assertThatThrownBy(() -> JobState.FAILED.onFailure(true))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("Terminal states")
  class TerminalStates {

    @Test
    @DisplayName("SUCCEEDED is terminal")
    void succeeded_isTerminal() {
      assertThat(JobState.SUCCEEDED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("FAILED is terminal")
    void failed_isTerminal() {
      assertThat(JobState.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("CANCELLED is terminal")
    void cancelled_isTerminal() {
      assertThat(JobState.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("SKIPPED is terminal")
    void skipped_isTerminal() {
      assertThat(JobState.SKIPPED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("PENDING is not terminal")
    void pending_isNotTerminal() {
      assertThat(JobState.PENDING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("QUEUED is not terminal")
    void queued_isNotTerminal() {
      assertThat(JobState.QUEUED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RUNNING is not terminal")
    void running_isNotTerminal() {
      assertThat(JobState.RUNNING.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("Database conversion")
  class DatabaseConversion {

    @Test
    @DisplayName("asDatabaseValue returns lowercase")
    void asDatabaseValue_returnsLowercase() {
      assertThat(JobState.PENDING.asDatabaseValue()).isEqualTo("pending");
      assertThat(JobState.QUEUED.asDatabaseValue()).isEqualTo("queued");
      assertThat(JobState.RUNNING.asDatabaseValue()).isEqualTo("running");
      assertThat(JobState.SUCCEEDED.asDatabaseValue()).isEqualTo("succeeded");
    }

    @Test
    @DisplayName("fromDatabase parses lowercase")
    void fromDatabase_parsesLowercase() {
      assertThat(JobState.fromDatabase("pending")).isEqualTo(JobState.PENDING);
      assertThat(JobState.fromDatabase("queued")).isEqualTo(JobState.QUEUED);
    }

    @Test
    @DisplayName("fromDatabase parses uppercase")
    void fromDatabase_parsesUppercase() {
      assertThat(JobState.fromDatabase("RUNNING")).isEqualTo(JobState.RUNNING);
    }

    @Test
    @DisplayName("fromDatabase rejects null")
    void fromDatabase_rejectsNull() {
      assertThatThrownBy(() -> JobState.fromDatabase(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromDatabase rejects blank")
    void fromDatabase_rejectsBlank() {
      assertThatThrownBy(() -> JobState.fromDatabase("  "))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("canTransitionTo")
  class CanTransitionTo {

    @Test
    @DisplayName("PENDING can transition to QUEUED, SKIPPED, CANCELLED")
    void pending_canTransitionToValidStates() {
      assertThat(JobState.PENDING.canTransitionTo(JobState.QUEUED)).isTrue();
      assertThat(JobState.PENDING.canTransitionTo(JobState.SKIPPED)).isTrue();
      assertThat(JobState.PENDING.canTransitionTo(JobState.CANCELLED)).isTrue();
      assertThat(JobState.PENDING.canTransitionTo(JobState.RUNNING)).isFalse();
    }

    @Test
    @DisplayName("QUEUED can transition to RUNNING, CANCELLED")
    void queued_canTransitionToValidStates() {
      assertThat(JobState.QUEUED.canTransitionTo(JobState.RUNNING)).isTrue();
      assertThat(JobState.QUEUED.canTransitionTo(JobState.CANCELLED)).isTrue();
      assertThat(JobState.QUEUED.canTransitionTo(JobState.SUCCEEDED)).isFalse();
    }

    @Test
    @DisplayName("RUNNING can transition to SUCCEEDED, FAILED, QUEUED, CANCELLED")
    void running_canTransitionToValidStates() {
      assertThat(JobState.RUNNING.canTransitionTo(JobState.SUCCEEDED)).isTrue();
      assertThat(JobState.RUNNING.canTransitionTo(JobState.FAILED)).isTrue();
      assertThat(JobState.RUNNING.canTransitionTo(JobState.QUEUED)).isTrue();
      assertThat(JobState.RUNNING.canTransitionTo(JobState.CANCELLED)).isTrue();
      assertThat(JobState.RUNNING.canTransitionTo(JobState.PENDING)).isFalse();
    }

    @Test
    @DisplayName("Terminal states cannot transition")
    void terminalStates_cannotTransition() {
      assertThat(JobState.SUCCEEDED.canTransitionTo(JobState.PENDING)).isFalse();
      assertThat(JobState.FAILED.canTransitionTo(JobState.PENDING)).isFalse();
      assertThat(JobState.CANCELLED.canTransitionTo(JobState.PENDING)).isFalse();
      assertThat(JobState.SKIPPED.canTransitionTo(JobState.PENDING)).isFalse();
    }
  }
}
