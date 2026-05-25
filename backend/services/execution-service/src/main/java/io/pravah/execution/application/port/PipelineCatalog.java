package io.pravah.execution.application.port;

import java.util.UUID;

/**
 * Loads pipeline read-model data from Pipeline Service (HTTP) for execution planning.
 *
 * <p>Authorization must be forwarded from the caller so Pipeline Service enforces the same tenant
 * JWT.
 */
public interface PipelineCatalog {

  /**
   * @param pipelineVersionOrNull when null, uses the pipeline's current published version
   */
  PublishedPipelineSnapshot resolve(
      UUID pipelineId, Integer pipelineVersionOrNull, String authorizationHeader);
}
