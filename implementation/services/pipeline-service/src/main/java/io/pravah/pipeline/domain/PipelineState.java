package io.pravah.pipeline.domain;

/**
 * Pipeline lifecycle states as defined in docs/lld/03-state-machines.md.
 *
 * <p>Transitions:
 *
 * <ul>
 *   <li>DRAFT → ACTIVE (publish)
 *   <li>ACTIVE → ACTIVE (update + publish new version)
 *   <li>ACTIVE → ARCHIVED (archive)
 *   <li>ARCHIVED → ACTIVE (restore)
 * </ul>
 *
 * <p>Database values are lowercase per {@code pipelines.status} column definition.
 */
public enum PipelineState {
  /** Initial state. Pipeline is being authored, not executable. */
  DRAFT {
    @Override
    public PipelineState onPublish() {
      return ACTIVE;
    }
  },

  /** Published and executable. Has at least one version. */
  ACTIVE {
    @Override
    public PipelineState onPublish() {
      return ACTIVE;
    }

    @Override
    public PipelineState onArchive() {
      return ARCHIVED;
    }
  },

  /** Soft-deleted. Not visible by default, not schedulable. */
  ARCHIVED {
    @Override
    public PipelineState onRestore() {
      return ACTIVE;
    }
  };

  /**
   * Transition to ACTIVE state by publishing a new version.
   *
   * @return the new state after publishing
   * @throws IllegalStateException if transition is not allowed
   */
  public PipelineState onPublish() {
    throw illegalTransition("publish");
  }

  /**
   * Transition to ARCHIVED state.
   *
   * @return the new state after archiving
   * @throws IllegalStateException if transition is not allowed
   */
  public PipelineState onArchive() {
    throw illegalTransition("archive");
  }

  /**
   * Transition to ACTIVE state by restoring an archived pipeline.
   *
   * @return the new state after restoring
   * @throws IllegalStateException if transition is not allowed
   */
  public PipelineState onRestore() {
    throw illegalTransition("restore");
  }

  /**
   * Checks if a transition to the target state is valid.
   *
   * @param target the target state
   * @return true if the transition is valid
   */
  public boolean canTransitionTo(PipelineState target) {
    return switch (this) {
      case DRAFT -> target == ACTIVE;
      case ACTIVE -> target == ACTIVE || target == ARCHIVED;
      case ARCHIVED -> target == ACTIVE;
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
   * @return the corresponding PipelineState
   * @throws IllegalArgumentException if the value is not a valid state
   */
  public static PipelineState fromDatabase(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Pipeline state cannot be null or blank");
    }
    return valueOf(value.toUpperCase());
  }

  private IllegalStateException illegalTransition(String action) {
    return new IllegalStateException(
        String.format("Cannot %s pipeline in state %s", action, this.name()));
  }
}
