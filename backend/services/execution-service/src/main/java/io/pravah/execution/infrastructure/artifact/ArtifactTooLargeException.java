package io.pravah.execution.infrastructure.artifact;

import io.pravah.common.exception.PravahException;

/**
 * Thrown when an artifact exceeds the maximum allowed size.
 */
public class ArtifactTooLargeException extends PravahException {

    private static final long serialVersionUID = 1L;

    public ArtifactTooLargeException(String message) {
        super("ARTIFACT_TOO_LARGE", message);
    }

    @Override
    public int suggestedHttpStatus() {
        return 413; // Payload Too Large
    }
}
