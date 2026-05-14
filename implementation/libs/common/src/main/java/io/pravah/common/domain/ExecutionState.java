package io.pravah.common.domain;

/**
 * Execution lifecycle states as defined in docs/lld/03-state-machines.md.
 *
 * <p>Transitions:
 *
 * <ul>
 *   <li>PENDING → RUNNING (first job starts)
 *   <li>PENDING → FAILED (validation failed)
 *   <li>PENDING → CANCELLED (user cancels)
 *   <li>RUNNING → SUCCEEDED (all jobs succeeded)
 *   <li>RUNNING → FAILED (any job failed after retries)
 *   <li>RUNNING → CANCELLED (user cancels)
 *   <li>FAILED → RETRYING (user retries)
 *   <li>RETRYING → RUNNING (first retried job starts)
 * </ul>
 *
 * <p>Terminal states: SUCCEEDED, FAILED (unless retried), CANCELLED
 *
 * <p>Database values are lowercase per {@code executions.status} column definition.
 *
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines</a>
 */
public enum ExecutionState {
  /** Execution created, waiting to start. */
  PENDING {
    @Override
    public ExecutionState onStart() {
      return RUNNING;
    }

    @Override
    public ExecutionState onFail() {
      return FAILED;
    }

    @Override
    public ExecutionState onCancel() {
      return CANCELLED;
    }
  },

  /** At least one job is executing. */
  RUNNING {
    @Override
    public ExecutionState onComplete(boolean success) {
      return success ? SUCCEEDED : FAILED;
    }

    @Override
    public ExecutionState onCancel() {
      return CANCELLED;
    }
  },

  /** All jobs completed successfully. Terminal state. */
  SUCCEEDED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  },

  /** At least one job failed after retry exhaustion. */
  FAILED {
    @Override
    public boolean isTerminal() {
      return true;
    }

    @Override
    public ExecutionState onRetry() {
      return RETRYING;
    }
  },

  /** User cancelled the execution. Terminal state. */
  CANCELLED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  },

  /** Failed execution being retried from a checkpoint. */
  RETRYING {
    @Override
    public ExecutionState onStart() {
      return RUNNING;
    }
  };

  /**
   * Transition when execution starts (first job begins).
   *
   * @return the new state after starting
   * @throws IllegalStateException if transition is not allowed
   */
  public ExecutionState onStart() {
    throw illegalTransition("start");
  }

  /**
   * Transition when execution completes.
   *
   * @param success true if all jobs succeeded, false if any failed
   * @return the new state after completion
   * @throws IllegalStateException if transition is not allowed
   */
  public ExecutionState onComplete(boolean success) {
    throw illegalTransition("complete");
  }

  /**
   * Transition when execution fails during validation or initial setup.
   *
   * @return the new state after failure
   * @throws IllegalStateException if transition is not allowed
   */
  public ExecutionState onFail() {
    throw illegalTransition("fail");
  }

  /**
   * Transition when user cancels the execution.
   *
   * @return the new state after cancellation
   * @throws IllegalStateException if transition is not allowed
   */
  public ExecutionState onCancel() {
    throw illegalTransition("cancel");
  }

  /**
   * Transition when user retries a failed execution.
   *
   * @return the new state after retry initiated
   * @throws IllegalStateException if transition is not allowed
   */
  public ExecutionState onRetry() {
    throw illegalTransition("retry");
  }

  /**
   * Checks if this is a terminal state.
   *
   * @return true if no further transitions are allowed (except retry from FAILED)
   */
  public boolean isTerminal() {
    return false;
  }

  /**
   * Checks if a transition to the target state is valid.
   *
   * @param target the target state
   * @return true if the transition is valid
   */
  public boolean canTransitionTo(ExecutionState target) {
    return switch (this) {
      case PENDING -> target == RUNNING || target == FAILED || target == CANCELLED;
      case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
      case SUCCEEDED -> false;
      case FAILED -> target == RETRYING;
      case CANCELLED -> false;
      case RETRYING -> target == RUNNING;
    };
  }

  /**
   * Returns the database representation (lowercase).
   *
   * @return lowercase state name
   */
  public String asDatabaseValue() {
    return name().toLowerCase();
  }

  /**
   * Parses a database value to the enum constant.
   *
   * @param value the database value (case-insensitive)
   * @return the corresponding ExecutionState
   * @throws IllegalArgumentException if the value is not a valid state
   */
  public static ExecutionState fromDatabase(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Execution state cannot be null or blank");
    }
    return valueOf(value.toUpperCase());
  }

  private IllegalStateException illegalTransition(String action) {
    return new IllegalStateException(
        String.format("Cannot %s execution in state %s", action, this.name()));
  }
}
