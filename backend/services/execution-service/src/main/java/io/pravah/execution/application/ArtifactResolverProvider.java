package io.pravah.execution.application;

import io.pravah.common.artifact.TenantArtifactKeys;
import io.pravah.common.domain.JobState;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.StageArtifactRef;
import io.pravah.common.domain.resolution.ValueReference;
import io.pravah.common.domain.resolution.ValueResolverProvider;
import io.pravah.execution.infrastructure.artifact.ArtifactStorageService;
import io.pravah.execution.infrastructure.artifact.ArtifactType;
import io.pravah.execution.infrastructure.artifact.PresignedUrlResponse;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code ${stages.stageId.artifact.filename}} references by looking up artifact metadata
 * from completed upstream jobs and generating presigned download URLs.
 *
 * <p>Artifact references are resolved at execution time (deferred) from the jobs table. The
 * referenced stage must have already completed successfully within the same execution.
 *
 * <p>Supported properties:
 *
 * <ul>
 *   <li>{@code url} — presigned download URL (default)
 *   <li>{@code key} — S3 key for direct access
 *   <li>{@code size_bytes} — artifact file size
 *   <li>{@code content_type} — MIME type
 * </ul>
 *
 * @see ArtifactStorageService
 */
@Component
public class ArtifactResolverProvider implements ValueResolverProvider {

  private final JobEntityRepository jobRepository;
  private final ArtifactStorageService artifactStorageService;

  public ArtifactResolverProvider(
      JobEntityRepository jobRepository, ArtifactStorageService artifactStorageService) {
    this.jobRepository = jobRepository;
    this.artifactStorageService = artifactStorageService;
  }

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof StageArtifactRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    StageArtifactRef artifactRef = (StageArtifactRef) ref;

    if (ctx.executionId() == null) {
      throw new IllegalStateException(
          "Cannot resolve artifact reference '%s' without an execution context"
              .formatted(artifactRef.raw()));
    }

    if (ctx.tenantId() == null) {
      throw new IllegalStateException(
          "Cannot resolve artifact reference '%s' without tenant context"
              .formatted(artifactRef.raw()));
    }

    String cacheKey =
        "artifact:"
            + artifactRef.stageId()
            + ":"
            + artifactRef.filename()
            + ":"
            + artifactRef.property();
    if (ctx.stageOutputCache().containsKey(cacheKey)) {
      return ctx.stageOutputCache().get(cacheKey);
    }

    JobEntity job =
        jobRepository
            .findByExecutionIdAndStageId(ctx.executionId(), artifactRef.stageId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Stage '%s' not found in execution".formatted(artifactRef.stageId())));

    if (job.getStatus() != JobState.SUCCEEDED) {
      throw new IllegalStateException(
          "Cannot access artifact of stage '%s' in state %s (expected SUCCEEDED)"
              .formatted(artifactRef.stageId(), job.getStatus()));
    }

    Map<String, Object> output = job.getOutput();
    if (output == null) {
      throw new IllegalStateException(
          "Stage '%s' has no output data".formatted(artifactRef.stageId()));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> artifacts = (Map<String, Object>) output.get("artifacts");
    if (artifacts == null || artifacts.isEmpty()) {
      throw new IllegalStateException(
          "Stage '%s' has no artifacts".formatted(artifactRef.stageId()));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> artifactMeta = (Map<String, Object>) artifacts.get(artifactRef.filename());
    if (artifactMeta == null) {
      throw new IllegalStateException(
          "Artifact '%s' not found in stage '%s'. Available: %s"
              .formatted(artifactRef.filename(), artifactRef.stageId(), artifacts.keySet()));
    }

    Object value = resolveProperty(artifactRef, artifactMeta, ctx);
    ctx.stageOutputCache().put(cacheKey, value);
    return value;
  }

  private Object resolveProperty(
      StageArtifactRef artifactRef, Map<String, Object> artifactMeta, ResolutionContext ctx) {

    String property = artifactRef.property();
    String key = (String) artifactMeta.get("key");
    if (key != null && ctx.tenantId() != null) {
      TenantArtifactKeys.requireOwnedByTenant(key, ctx.tenantId());
    }

    return switch (property) {
      case "key" -> key;
      case "size_bytes" -> artifactMeta.get("size_bytes");
      case "content_type" -> artifactMeta.get("content_type");
      case "url" -> {
        PresignedUrlResponse presignedUrl =
            artifactStorageService.generateDownloadUrl(
                ctx.tenantId().toString(),
                ctx.executionId(),
                getJobIdFromKey(key),
                ArtifactType.OUTPUT,
                artifactRef.filename());
        yield presignedUrl.url();
      }
      default -> throw new IllegalArgumentException("Unknown artifact property: " + property);
    };
  }

  private java.util.UUID getJobIdFromKey(String key) {
    String[] parts = key.split("/");
    for (int i = 0; i < parts.length; i++) {
      if ("jobs".equals(parts[i]) && i + 1 < parts.length) {
        return java.util.UUID.fromString(parts[i + 1]);
      }
    }
    throw new IllegalStateException("Cannot extract job ID from artifact key: " + key);
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Runtime validation only — artifact may not exist yet at publish time
  }
}
