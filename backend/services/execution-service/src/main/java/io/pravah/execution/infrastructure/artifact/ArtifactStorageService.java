package io.pravah.execution.infrastructure.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing artifacts in S3/MinIO storage.
 * Handles upload, download, presigned URL generation, and lifecycle management.
 *
 * Bucket layout:
 * pravah-artifacts/
 * └── tenants/{tenant_id}/
 *     └── executions/{execution_id}/
 *         └── jobs/{job_id}/
 *             ├── output/
 *             │   ├── data.parquet
 *             │   └── data.json
 *             └── logs/
 *                 └── execution.log.gz
 *
 * @see docs/adr/ADR-021-minio-artifact-storage.md
 */
public class ArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ArtifactStorageProperties props;

    public ArtifactStorageService(S3Client s3Client, S3Presigner s3Presigner, ArtifactStorageProperties props) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.props = props;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
            log.debug("Artifact bucket '{}' exists", props.bucket());
        } catch (NoSuchBucketException e) {
            log.info("Creating artifact bucket '{}'", props.bucket());
            s3Client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
        } catch (Exception e) {
            log.warn("Could not check/create bucket '{}': {}. Artifact storage may not be available.",
                    props.bucket(), e.getMessage());
        }
    }

    /**
     * Build the S3 key for an artifact.
     */
    public String buildKey(String tenantId, UUID executionId, UUID jobId, ArtifactType type, String filename) {
        return String.format("tenants/%s/executions/%s/jobs/%s/%s/%s",
                tenantId, executionId, jobId, type.getFolder(), filename);
    }

    /**
     * Generate a presigned URL for uploading an artifact.
     * Runners use this to upload directly to MinIO without credentials.
     */
    public PresignedUrlResponse generateUploadUrl(String tenantId, UUID executionId, UUID jobId,
                                                   ArtifactType type, String filename, String contentType) {
        String key = buildKey(tenantId, executionId, jobId, type, filename);

        var putRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .metadata(Map.of(
                        "tenant-id", tenantId,
                        "execution-id", executionId.toString(),
                        "job-id", jobId.toString(),
                        "artifact-type", type.name()
                ))
                .build();

        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(props.presignedUrlExpiry())
                .putObjectRequest(putRequest)
                .build();

        var presignedUrl = s3Presigner.presignPutObject(presignRequest);

        log.debug("Generated upload URL for artifact: key={}, expires={}",
                key, presignedUrl.expiration());

        return new PresignedUrlResponse(
                presignedUrl.url().toString(),
                key,
                presignedUrl.expiration(),
                props.maxArtifactSizeBytes()
        );
    }

    /**
     * Generate a presigned URL for downloading an artifact.
     */
    public PresignedUrlResponse generateDownloadUrl(String tenantId, UUID executionId, UUID jobId,
                                                     ArtifactType type, String filename) {
        String key = buildKey(tenantId, executionId, jobId, type, filename);

        var getRequest = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .build();

        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(props.presignedUrlExpiry())
                .getObjectRequest(getRequest)
                .build();

        var presignedUrl = s3Presigner.presignGetObject(presignRequest);

        return new PresignedUrlResponse(
                presignedUrl.url().toString(),
                key,
                presignedUrl.expiration(),
                0
        );
    }

    /**
     * Upload artifact content directly (for embedded executors).
     */
    public ArtifactMetadata upload(String tenantId, UUID executionId, UUID jobId,
                                   ArtifactType type, String filename, InputStream content,
                                   long contentLength, String contentType) {
        String key = buildKey(tenantId, executionId, jobId, type, filename);

        if (contentLength > props.maxArtifactSizeBytes()) {
            throw new ArtifactTooLargeException(
                    "Artifact size " + contentLength + " exceeds maximum " + props.maxArtifactSizeBytes());
        }

        var putRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .metadata(Map.of(
                        "tenant-id", tenantId,
                        "execution-id", executionId.toString(),
                        "job-id", jobId.toString(),
                        "artifact-type", type.name()
                ))
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(content, contentLength));

        log.info("Uploaded artifact: key={}, size={}", key, contentLength);

        return new ArtifactMetadata(
                key,
                filename,
                type,
                contentLength,
                contentType,
                Instant.now()
        );
    }

    /**
     * Upload artifact from byte array.
     */
    public ArtifactMetadata upload(String tenantId, UUID executionId, UUID jobId,
                                   ArtifactType type, String filename, byte[] content, String contentType) {
        String key = buildKey(tenantId, executionId, jobId, type, filename);

        if (content.length > props.maxArtifactSizeBytes()) {
            throw new ArtifactTooLargeException(
                    "Artifact size " + content.length + " exceeds maximum " + props.maxArtifactSizeBytes());
        }

        var putRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .metadata(Map.of(
                        "tenant-id", tenantId,
                        "execution-id", executionId.toString(),
                        "job-id", jobId.toString(),
                        "artifact-type", type.name()
                ))
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(content));

        log.info("Uploaded artifact: key={}, size={}", key, content.length);

        return new ArtifactMetadata(
                key,
                filename,
                type,
                content.length,
                contentType,
                Instant.now()
        );
    }

    /**
     * Download artifact content.
     */
    public byte[] download(String key) {
        var getRequest = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .build();

        try (var response = s3Client.getObject(getRequest)) {
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new ArtifactNotFoundException("Artifact not found: " + key);
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to download artifact: " + key, e);
        }
    }

    /**
     * List all artifacts for a job.
     */
    public List<ArtifactMetadata> listJobArtifacts(String tenantId, UUID executionId, UUID jobId) {
        String prefix = String.format("tenants/%s/executions/%s/jobs/%s/",
                tenantId, executionId, jobId);

        var listRequest = ListObjectsV2Request.builder()
                .bucket(props.bucket())
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);

        return response.contents().stream()
                .map(obj -> {
                    String key = obj.key();
                    String filename = key.substring(key.lastIndexOf('/') + 1);
                    ArtifactType type = determineArtifactType(key);
                    return new ArtifactMetadata(
                            key,
                            filename,
                            type,
                            obj.size(),
                            null,
                            obj.lastModified()
                    );
                })
                .toList();
    }

    /**
     * List all artifacts for an execution.
     */
    public List<ArtifactMetadata> listExecutionArtifacts(String tenantId, UUID executionId) {
        String prefix = String.format("tenants/%s/executions/%s/", tenantId, executionId);

        var listRequest = ListObjectsV2Request.builder()
                .bucket(props.bucket())
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);

        return response.contents().stream()
                .map(obj -> {
                    String key = obj.key();
                    String filename = key.substring(key.lastIndexOf('/') + 1);
                    ArtifactType type = determineArtifactType(key);
                    return new ArtifactMetadata(
                            key,
                            filename,
                            type,
                            obj.size(),
                            null,
                            obj.lastModified()
                    );
                })
                .toList();
    }

    /**
     * Delete all artifacts for an execution.
     */
    public void deleteExecutionArtifacts(String tenantId, UUID executionId) {
        String prefix = String.format("tenants/%s/executions/%s/", tenantId, executionId);

        var listRequest = ListObjectsV2Request.builder()
                .bucket(props.bucket())
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);

        if (response.contents().isEmpty()) {
            log.debug("No artifacts to delete for execution {}", executionId);
            return;
        }

        var objectsToDelete = response.contents().stream()
                .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                .toList();

        var deleteRequest = DeleteObjectsRequest.builder()
                .bucket(props.bucket())
                .delete(Delete.builder().objects(objectsToDelete).build())
                .build();

        s3Client.deleteObjects(deleteRequest);
        log.info("Deleted {} artifacts for execution {}", objectsToDelete.size(), executionId);
    }

    /**
     * Check if an artifact exists.
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Get artifact metadata without downloading.
     */
    public Optional<ArtifactMetadata> getMetadata(String key) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(key)
                    .build());

            String filename = key.substring(key.lastIndexOf('/') + 1);
            ArtifactType type = determineArtifactType(key);

            return Optional.of(new ArtifactMetadata(
                    key,
                    filename,
                    type,
                    response.contentLength(),
                    response.contentType(),
                    response.lastModified()
            ));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    private ArtifactType determineArtifactType(String key) {
        if (key.contains("/output/")) return ArtifactType.OUTPUT;
        if (key.contains("/logs/")) return ArtifactType.LOG;
        if (key.contains("/profile/")) return ArtifactType.PROFILE;
        if (key.contains("/checkpoint/")) return ArtifactType.CHECKPOINT;
        return ArtifactType.OTHER;
    }

    /**
     * Check if the artifact storage is healthy.
     */
    public boolean isHealthy() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
            return true;
        } catch (Exception e) {
            log.warn("Artifact storage health check failed: {}", e.getMessage());
            return false;
        }
    }
}
