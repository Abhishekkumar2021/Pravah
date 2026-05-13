package io.pravah.common.exception;

/**
 * Thrown when an operation would result in an invalid state transition.
 *
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines</a>
 */
public class InvalidStateTransitionException extends PravahException {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String entityId;
    private final String currentState;
    private final String attemptedTransition;

    public InvalidStateTransitionException(
            String entityType,
            String entityId,
            String currentState,
            String attemptedTransition) {
        super(
            entityType.toUpperCase() + "_INVALID_STATE",
            String.format(
                "Cannot perform '%s' on %s '%s' in state '%s'",
                attemptedTransition, entityType, entityId, currentState
            )
        );
        this.entityType = entityType;
        this.entityId = entityId;
        this.currentState = currentState;
        this.attemptedTransition = attemptedTransition;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getAttemptedTransition() {
        return attemptedTransition;
    }

    @Override
    public int suggestedHttpStatus() {
        return 409; // Conflict
    }
}
