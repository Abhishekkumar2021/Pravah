package io.pravah.common.exception;

/**
 * Thrown when a concurrent modification conflict is detected.
 * Typically caused by optimistic locking failures.
 */
public class ConcurrencyException extends PravahException {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String entityId;

    public ConcurrencyException(String entityType, String entityId) {
        super(
            "CONCURRENT_MODIFICATION",
            String.format(
                "%s '%s' was modified by another request. Please retry.",
                entityType, entityId
            )
        );
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    @Override
    public int suggestedHttpStatus() {
        return 409; // Conflict
    }
}
