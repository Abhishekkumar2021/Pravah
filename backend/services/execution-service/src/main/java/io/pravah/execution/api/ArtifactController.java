package io.pravah.execution.api;

import io.pravah.execution.infrastructure.artifact.*;
import io.pravah.spring.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for artifact management.
 *
 * Artifacts are stored in MinIO (S3-compatible) and accessed via presigned URLs.
 * Runners upload/download directly to MinIO; this API only generates URLs.
 *
 * @see docs/adr/ADR-021-minio-artifact-storage.md
 */
@RestController
@RequestMapping("/api/v1/artifacts")
@Tag(name = "Artifacts", description = "Artifact storage operations")
public class ArtifactController {

    private final ArtifactStorageService artifactStorageService;

    public ArtifactController(ArtifactStorageService artifactStorageService) {
        this.artifactStorageService = artifactStorageService;
    }

    private String requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId.toString();
    }

    @Operation(summary = "Generate upload URL", description = "Generate a presigned URL for uploading an artifact")
    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUrlResponse> generateUploadUrl(
            @RequestBody GenerateUploadUrlRequest request) {

        var response = artifactStorageService.generateUploadUrl(
                requireTenantId(),
                request.executionId(),
                request.jobId(),
                request.type(),
                request.filename(),
                request.contentType()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Generate download URL", description = "Generate a presigned URL for downloading an artifact")
    @PostMapping("/download-url")
    public ResponseEntity<PresignedUrlResponse> generateDownloadUrl(
            @RequestBody GenerateDownloadUrlRequest request) {

        var response = artifactStorageService.generateDownloadUrl(
                requireTenantId(),
                request.executionId(),
                request.jobId(),
                request.type(),
                request.filename()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List execution artifacts", description = "List all artifacts for an execution")
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<List<ArtifactMetadata>> listExecutionArtifacts(
            @PathVariable UUID executionId) {

        var artifacts = artifactStorageService.listExecutionArtifacts(requireTenantId(), executionId);
        return ResponseEntity.ok(artifacts);
    }

    @Operation(summary = "List job artifacts", description = "List all artifacts for a specific job")
    @GetMapping("/executions/{executionId}/jobs/{jobId}")
    public ResponseEntity<List<ArtifactMetadata>> listJobArtifacts(
            @PathVariable UUID executionId,
            @PathVariable UUID jobId) {

        var artifacts = artifactStorageService.listJobArtifacts(requireTenantId(), executionId, jobId);
        return ResponseEntity.ok(artifacts);
    }

    @Operation(summary = "Get artifact metadata", description = "Get metadata for a specific artifact")
    @GetMapping("/metadata")
    public ResponseEntity<ArtifactMetadata> getArtifactMetadata(
            @RequestParam String key) {

        return artifactStorageService.getMetadata(key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ArtifactNotFoundException("Artifact not found: " + key));
    }

    @Operation(summary = "Delete execution artifacts", description = "Delete all artifacts for an execution")
    @DeleteMapping("/executions/{executionId}")
    public ResponseEntity<Void> deleteExecutionArtifacts(
            @PathVariable UUID executionId) {

        artifactStorageService.deleteExecutionArtifacts(requireTenantId(), executionId);
        return ResponseEntity.noContent().build();
    }

    public record GenerateUploadUrlRequest(
            UUID executionId,
            UUID jobId,
            ArtifactType type,
            String filename,
            String contentType
    ) {}

    public record GenerateDownloadUrlRequest(
            UUID executionId,
            UUID jobId,
            ArtifactType type,
            String filename
    ) {}
}
