package io.pravah.common.exception;

import java.util.UUID;

/** Thrown when a requested entity does not exist. */
public class EntityNotFoundException extends PravahException {

  private static final long serialVersionUID = 1L;

  private final String entityType;
  private final String entityId;

  public EntityNotFoundException(String entityType, String entityId) {
    super(
        entityType.toUpperCase() + "_NOT_FOUND",
        String.format("%s with ID '%s' not found", entityType, entityId));
    this.entityType = entityType;
    this.entityId = entityId;
  }

  public EntityNotFoundException(String entityType, UUID entityId) {
    this(entityType, entityId.toString());
  }

  public EntityNotFoundException(String message) {
    super("ENTITY_NOT_FOUND", message);
    this.entityType = "Unknown";
    this.entityId = null;
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
