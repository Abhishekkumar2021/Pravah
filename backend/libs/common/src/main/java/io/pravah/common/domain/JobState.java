package io.pravah.common.domain;

/**
 * Job lifecycle states as defined in docs/lld/03-state-machines.md.
 *
 * <p>Jobs are individual stage executions within an Execution.
 *
 * <p>Transitions:
 *
 * <ul>
 *   <li>PENDING → QUEUED (dependencies met)
 *   <li>PENDING → SKIPPED (skip condition true)
 *   <li>PENDING → CANCELLED (parent execution cancelled before queue)
 *   <li>QUEUED → RUNNING (runner assigned)
 *   <li>RUNNING → SUCCEEDED (success)
 *   <li>RUNNING → FAILED (failure after max retries)
 *   <li>RUNNING → QUEUED (retry)
 *   <li>RUNNING → CANCELLED (parent execution cancelled)
 * </ul>
 *
 * <p>Terminal states: SUCCEEDED, FAILED, CANCELLED, SKIPPED
 *
 * <p>Database values are lowercase per {@code jobs.status} column definition.
 *
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Job</a>
 */
public enum JobState {
  /** Job created, waiting for dependencies. */
  PENDING {
    @Override
    public JobState onDependenciesMet() {
      return QUEUED;
    }

    @Override
    public JobState onSkip() {
      return SKIPPED;
    }

    @Override
    public JobState onCancel() {
      return CANCELLED;
    }
  },

  /** Dependencies met, waiting for runner assignment. */
  QUEUED {
    @Override
    public JobState onAssign() {
      return RUNNING;
    }

    @Override
    public JobState onCancel() {
      return CANCELLED;
    }
  },

  /** Assigned to runner, executing. */
  RUNNING {
    @Override
    public JobState onSuccess() {
      return SUCCEEDED;
    }

    @Override
    public JobState onFailure(boolean canRetry) {
      return canRetry ? QUEUED : FAILED;
    }

    @Override
    public JobState onCancel() {
      return CANCELLED;
    }
  },

  /** Completed successfully. Terminal state. */
  SUCCEEDED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  },

  /** Failed after all retry attempts. Terminal state. */
  FAILED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  },

  /** Parent execution cancelled. Terminal state. */
  CANCELLED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  },

  /** Skip condition evaluated to true. Terminal state. */
  SKIPPED {
    @Override
    public boolean isTerminal() {
      return true;
    }
  };

  /**
   * Transition when all upstream dependencies are satisfied.
   *
   * @return the new state (QUEUED)
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onDependenciesMet() {
    throw illegalTransition("queue");
  }

  /**
   * Transition when skip condition is met.
   *
   * @return the new state (SKIPPED)
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onSkip() {
    throw illegalTransition("skip");
  }

  /**
   * Transition when a runner is assigned.
   *
   * @return the new state (RUNNING)
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onAssign() {
    throw illegalTransition("assign");
  }

  /**
   * Transition when job completes successfully.
   *
   * @return the new state (SUCCEEDED)
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onSuccess() {
    throw illegalTransition("succeed");
  }

  /**
   * Transition when job fails.
   *
   * @param canRetry true if retry attempts remain
   * @return QUEUED if can retry, FAILED otherwise
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onFailure(boolean canRetry) {
    throw illegalTransition("fail");
  }

  /**
   * Transition when parent execution is cancelled.
   *
   * @return the new state (CANCELLED)
   * @throws IllegalStateException if transition is not allowed
   */
  public JobState onCancel() {
    throw illegalTransition("cancel");
  }

  /**
   * Checks if this is a terminal state.
   *
   * @return true if no further transitions are allowed
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
  public boolean canTransitionTo(JobState target) {
    return switch (this) {
      case PENDING -> target == QUEUED || target == SKIPPED || target == CANCELLED;
      case QUEUED -> target == RUNNING || target == CANCELLED;
      case RUNNING ->
          target == SUCCEEDED || target == FAILED || target == QUEUED || target == CANCELLED;
      case SUCCEEDED, FAILED, CANCELLED, SKIPPED -> false;
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
   * @return the corresponding JobState
   * @throws IllegalArgumentException if the value is not a valid state
   */
  public static JobState fromDatabase(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Job state cannot be null or blank");
    }
    return valueOf(value.toUpperCase());
  }

  private IllegalStateException illegalTransition(String action) {
    return new IllegalStateException(
        String.format("Cannot %s job in state %s", action, this.name()));
  }
}
