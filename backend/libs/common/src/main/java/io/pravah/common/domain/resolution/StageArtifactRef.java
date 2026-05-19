package io.pravah.common.domain.resolution;

/**
 * Reference to an upstream stage's artifact: {@code ${stages.stageId.artifact.filename}}.
 *
 * <p>Resolved at stage execution time (deferred) by looking up the artifact metadata from the
 * completed upstream job's output. Returns a presigned URL for downloading the artifact.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code ${stages.extract.artifact.result.json.gz}} — artifact file key
 *   <li>{@code ${stages.transform.artifact.output.csv}} — named output file
 * </ul>
 *
 * <p>The property can be:
 *
 * <ul>
 *   <li>{@code key} — returns the S3 key for direct access
 *   <li>{@code url} — returns a presigned download URL (default)
 *   <li>{@code size_bytes} — returns the artifact size
 *   <li>{@code content_type} — returns the MIME type
 * </ul>
 *
 * @param stageId the upstream stage ID (must exist in the same pipeline)
 * @param filename the artifact filename
 * @param property the property to retrieve (key, url, size_bytes, content_type)
 */
public record StageArtifactRef(String stageId, String filename, String property)
    implements ValueReference {

  public static final String DEFAULT_PROPERTY = "url";

  public StageArtifactRef {
    if (stageId == null || stageId.isBlank()) {
      throw new IllegalArgumentException("Stage ID is required for artifact reference");
    }
    if (!stageId.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) {
      throw new IllegalArgumentException("Invalid stage ID: " + stageId);
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("Filename is required for artifact reference");
    }
    if (property == null || property.isBlank()) {
      property = DEFAULT_PROPERTY;
    }
    if (!property.matches("key|url|size_bytes|content_type")) {
      throw new IllegalArgumentException(
          "Invalid artifact property: "
              + property
              + ". Supported: key, url, size_bytes, content_type");
    }
  }

  public StageArtifactRef(String stageId, String filename) {
    this(stageId, filename, DEFAULT_PROPERTY);
  }

  @Override
  public String raw() {
    if (DEFAULT_PROPERTY.equals(property)) {
      return "${stages." + stageId + ".artifact." + filename + "}";
    }
    return "${stages." + stageId + ".artifact." + filename + "." + property + "}";
  }

  @Override
  public boolean isDeferred() {
    return true;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.STAGE_ARTIFACT;
  }
}
