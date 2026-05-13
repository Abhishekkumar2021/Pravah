package io.pravah.common.exception;

/**
 * Thrown when a requested entity does not exist.
 */
public class EntityNotFoundException extends PravahException {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String entityId;

    public EntityNotFoundException(String entityType, String entityId) {
        super(
            entityType.toUpperCase() + "_NOT_FOUND",
            String.format("%s with ID '%s' not found", entityType, entityId)
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
        return 404;
    }
}
