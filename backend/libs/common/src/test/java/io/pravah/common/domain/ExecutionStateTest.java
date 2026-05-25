package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionState")
class ExecutionStateTest {

  @Nested
  @DisplayName("State transitions")
  class StateTransitions {

    @Test
    @DisplayName("PENDING → RUNNING on start")
    void pending_onStart_transitionsToRunning() {
      assertThat(ExecutionState.PENDING.onStart()).isEqualTo(ExecutionState.RUNNING);
    }

    @Test
    @DisplayName("PENDING → FAILED on fail")
    void pending_onFail_transitionsToFailed() {
      assertThat(ExecutionState.PENDING.onFail()).isEqualTo(ExecutionState.FAILED);
    }

    @Test
    @DisplayName("PENDING → CANCELLED on cancel")
    void pending_onCancel_transitionsToCancelled() {
      assertThat(ExecutionState.PENDING.onCancel()).isEqualTo(ExecutionState.CANCELLED);
    }

    @Test
    @DisplayName("RUNNING → SUCCEEDED on complete(true)")
    void running_onCompleteSuccess_transitionsToSucceeded() {
      assertThat(ExecutionState.RUNNING.onComplete(true)).isEqualTo(ExecutionState.SUCCEEDED);
    }

    @Test
    @DisplayName("RUNNING → FAILED on complete(false)")
    void running_onCompleteFailure_transitionsToFailed() {
      assertThat(ExecutionState.RUNNING.onComplete(false)).isEqualTo(ExecutionState.FAILED);
    }

    @Test
    @DisplayName("RUNNING → CANCELLED on cancel")
    void running_onCancel_transitionsToCancelled() {
      assertThat(ExecutionState.RUNNING.onCancel()).isEqualTo(ExecutionState.CANCELLED);
    }

    @Test
    @DisplayName("FAILED → RETRYING on retry")
    void failed_onRetry_transitionsToRetrying() {
      assertThat(ExecutionState.FAILED.onRetry()).isEqualTo(ExecutionState.RETRYING);
    }

    @Test
    @DisplayName("RETRYING → RUNNING on start")
    void retrying_onStart_transitionsToRunning() {
      assertThat(ExecutionState.RETRYING.onStart()).isEqualTo(ExecutionState.RUNNING);
    }
  }

  @Nested
  @DisplayName("Invalid transitions")
  class InvalidTransitions {

    @Test
    @DisplayName("SUCCEEDED cannot start")
    void succeeded_onStart_throws() {
      assertThatThrownBy(() -> ExecutionState.SUCCEEDED.onStart())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot start execution in state SUCCEEDED");
    }

    @Test
    @DisplayName("SUCCEEDED cannot retry")
    void succeeded_onRetry_throws() {
      assertThatThrownBy(() -> ExecutionState.SUCCEEDED.onRetry())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CANCELLED cannot start")
    void cancelled_onStart_throws() {
      assertThatThrownBy(() -> ExecutionState.CANCELLED.onStart())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RUNNING cannot start")
    void running_onStart_throws() {
      assertThatThrownBy(() -> ExecutionState.RUNNING.onStart())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PENDING cannot complete")
    void pending_onComplete_throws() {
      assertThatThrownBy(() -> ExecutionState.PENDING.onComplete(true))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("Terminal states")
  class TerminalStates {

    @Test
    @DisplayName("SUCCEEDED is terminal")
    void succeeded_isTerminal() {
      assertThat(ExecutionState.SUCCEEDED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("FAILED is terminal")
    void failed_isTerminal() {
      assertThat(ExecutionState.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("CANCELLED is terminal")
    void cancelled_isTerminal() {
      assertThat(ExecutionState.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("PENDING is not terminal")
    void pending_isNotTerminal() {
      assertThat(ExecutionState.PENDING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RUNNING is not terminal")
    void running_isNotTerminal() {
      assertThat(ExecutionState.RUNNING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RETRYING is not terminal")
    void retrying_isNotTerminal() {
      assertThat(ExecutionState.RETRYING.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("Database conversion")
  class DatabaseConversion {

    @Test
    @DisplayName("asDatabaseValue returns lowercase")
    void asDatabaseValue_returnsLowercase() {
      assertThat(ExecutionState.PENDING.asDatabaseValue()).isEqualTo("pending");
      assertThat(ExecutionState.RUNNING.asDatabaseValue()).isEqualTo("running");
      assertThat(ExecutionState.SUCCEEDED.asDatabaseValue()).isEqualTo("succeeded");
    }

    @Test
    @DisplayName("fromDatabase parses lowercase")
    void fromDatabase_parsesLowercase() {
      assertThat(ExecutionState.fromDatabase("pending")).isEqualTo(ExecutionState.PENDING);
      assertThat(ExecutionState.fromDatabase("running")).isEqualTo(ExecutionState.RUNNING);
    }

    @Test
    @DisplayName("fromDatabase parses uppercase")
    void fromDatabase_parsesUppercase() {
      assertThat(ExecutionState.fromDatabase("SUCCEEDED")).isEqualTo(ExecutionState.SUCCEEDED);
    }

    @Test
    @DisplayName("fromDatabase rejects null")
    void fromDatabase_rejectsNull() {
      assertThatThrownBy(() -> ExecutionState.fromDatabase(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromDatabase rejects blank")
    void fromDatabase_rejectsBlank() {
      assertThatThrownBy(() -> ExecutionState.fromDatabase("  "))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("canTransitionTo")
  class CanTransitionTo {

    @Test
    @DisplayName("PENDING can transition to RUNNING, FAILED, CANCELLED")
    void pending_canTransitionToValidStates() {
      assertThat(ExecutionState.PENDING.canTransitionTo(ExecutionState.RUNNING)).isTrue();
      assertThat(ExecutionState.PENDING.canTransitionTo(ExecutionState.FAILED)).isTrue();
      assertThat(ExecutionState.PENDING.canTransitionTo(ExecutionState.CANCELLED)).isTrue();
      assertThat(ExecutionState.PENDING.canTransitionTo(ExecutionState.SUCCEEDED)).isFalse();
    }

    @Test
    @DisplayName("RUNNING can transition to SUCCEEDED, FAILED, CANCELLED")
    void running_canTransitionToValidStates() {
      assertThat(ExecutionState.RUNNING.canTransitionTo(ExecutionState.SUCCEEDED)).isTrue();
      assertThat(ExecutionState.RUNNING.canTransitionTo(ExecutionState.FAILED)).isTrue();
      assertThat(ExecutionState.RUNNING.canTransitionTo(ExecutionState.CANCELLED)).isTrue();
      assertThat(ExecutionState.RUNNING.canTransitionTo(ExecutionState.PENDING)).isFalse();
    }

    @Test
    @DisplayName("FAILED can only transition to RETRYING")
    void failed_canOnlyTransitionToRetrying() {
      assertThat(ExecutionState.FAILED.canTransitionTo(ExecutionState.RETRYING)).isTrue();
      assertThat(ExecutionState.FAILED.canTransitionTo(ExecutionState.RUNNING)).isFalse();
      assertThat(ExecutionState.FAILED.canTransitionTo(ExecutionState.PENDING)).isFalse();
    }

    @Test
    @DisplayName("Terminal states cannot transition")
    void terminalStates_cannotTransition() {
      assertThat(ExecutionState.SUCCEEDED.canTransitionTo(ExecutionState.PENDING)).isFalse();
      assertThat(ExecutionState.CANCELLED.canTransitionTo(ExecutionState.PENDING)).isFalse();
    }
  }
}
