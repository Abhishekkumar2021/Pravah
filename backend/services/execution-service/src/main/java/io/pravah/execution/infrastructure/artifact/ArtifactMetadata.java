package io.pravah.execution.infrastructure.artifact;

import java.time.Instant;

/**
 * Metadata about a stored artifact.
 */
public record ArtifactMetadata(
        String key,
        String filename,
        ArtifactType type,
        long sizeBytes,
        String contentType,
        Instant createdAt
) {
    public String humanReadableSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.2f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }
}
