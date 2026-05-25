package io.pravah.common.domain.resolution;

/**
 * Reference to an upstream stage's output: {@code ${stages.stageId.output.key}}.
 *
 * <p>Resolved at stage execution time (deferred) by querying the completed upstream job's output
 * from the database. The referenced stage must have already completed successfully.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code ${stages.extract.output.row_count}} — top-level key
 *   <li>{@code ${stages.extract.output.preview.0.id}} — nested access via dot notation
 * </ul>
 *
 * @param stageId the upstream stage ID (must exist in the same pipeline)
 * @param outputPath the path to the output value (supports nested dot notation)
 */
public record StageOutputRef(String stageId, String outputPath) implements ValueReference {

  public StageOutputRef {
    if (stageId == null || stageId.isBlank()) {
      throw new IllegalArgumentException("Stage ID is required for stage output reference");
    }
    if (!stageId.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) {
      throw new IllegalArgumentException("Invalid stage ID: " + stageId);
    }
    if (outputPath == null || outputPath.isBlank()) {
      throw new IllegalArgumentException("Output path is required for stage output reference");
    }
    if (!outputPath.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
      throw new IllegalArgumentException("Invalid output path: " + outputPath);
    }
  }

  @Override
  public String raw() {
    return "${stages." + stageId + ".output." + outputPath + "}";
  }

  @Override
  public boolean isDeferred() {
    return true;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.STAGE_OUTPUT;
  }
}
