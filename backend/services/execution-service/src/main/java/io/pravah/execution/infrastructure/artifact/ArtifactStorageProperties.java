package io.pravah.execution.infrastructure.artifact;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for artifact storage (MinIO/S3).
 *
 * @see docs/adr/ADR-021-minio-artifact-storage.md
 */
@ConfigurationProperties(prefix = "pravah.artifact")
public record ArtifactStorageProperties(
    boolean enabled,
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucket,
    Duration presignedUrlExpiry,
    long maxArtifactSizeBytes,
    int retentionDays) {
  public ArtifactStorageProperties {
    if (enabled == false) {
      endpoint = endpoint != null ? endpoint : "http://localhost:9000";
      region = region != null ? region : "us-east-1";
      accessKey = accessKey != null ? accessKey : "pravah";
      secretKey = secretKey != null ? secretKey : "pravah123";
      bucket = bucket != null ? bucket : "pravah-artifacts";
      presignedUrlExpiry = presignedUrlExpiry != null ? presignedUrlExpiry : Duration.ofMinutes(15);
      maxArtifactSizeBytes =
          maxArtifactSizeBytes > 0 ? maxArtifactSizeBytes : 5L * 1024 * 1024 * 1024; // 5GB
      retentionDays = retentionDays > 0 ? retentionDays : 30;
    }
  }

  public static ArtifactStorageProperties defaults() {
    return new ArtifactStorageProperties(
        true,
        "http://localhost:9000",
        "us-east-1",
        "pravah",
        "pravah123",
        "pravah-artifacts",
        Duration.ofMinutes(15),
        5L * 1024 * 1024 * 1024,
        30);
  }
}
