package io.pravah.execution.infrastructure.artifact;

import java.time.Instant;

/**
 * Response containing a presigned URL for artifact upload/download.
 */
public record PresignedUrlResponse(
        String url,
        String key,
        Instant expiresAt,
        long maxSizeBytes
) {
}
