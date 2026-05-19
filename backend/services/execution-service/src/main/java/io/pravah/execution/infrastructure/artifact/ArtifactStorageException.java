package io.pravah.execution.infrastructure.artifact;

import io.pravah.common.exception.PravahException;

/**
 * Generic exception for artifact storage operations.
 */
public class ArtifactStorageException extends PravahException {

    private static final long serialVersionUID = 1L;

    public ArtifactStorageException(String message) {
        super("ARTIFACT_STORAGE_ERROR", message);
    }

    public ArtifactStorageException(String message, Throwable cause) {
        super("ARTIFACT_STORAGE_ERROR", message, cause);
    }

    @Override
    public int suggestedHttpStatus() {
        return 500;
    }
}
