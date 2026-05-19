package io.pravah.execution.infrastructure.artifact;

/**
 * Types of artifacts that can be stored.
 */
public enum ArtifactType {
    OUTPUT("output"),
    LOG("logs"),
    PROFILE("profile"),
    CHECKPOINT("checkpoint"),
    OTHER("other");

    private final String folder;

    ArtifactType(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }
}
