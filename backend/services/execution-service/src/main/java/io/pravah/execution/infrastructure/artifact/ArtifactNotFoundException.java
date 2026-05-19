package io.pravah.execution.infrastructure.artifact;

import io.pravah.common.exception.EntityNotFoundException;

/**
 * Thrown when a requested artifact does not exist.
 */
public class ArtifactNotFoundException extends EntityNotFoundException {

    private static final long serialVersionUID = 1L;

    public ArtifactNotFoundException(String message) {
        super(message);
    }
}
