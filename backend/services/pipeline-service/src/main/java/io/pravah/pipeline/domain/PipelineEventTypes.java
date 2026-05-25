package io.pravah.pipeline.domain;

/** Domain event type names for pipeline aggregate (topic + outbox). */
public final class PipelineEventTypes {

  public static final String PIPELINE_CREATED = "pipeline.created";
  public static final String PIPELINE_UPDATED = "pipeline.updated";
  public static final String PIPELINE_PUBLISHED = "pipeline.published";
  public static final String PIPELINE_ARCHIVED = "pipeline.archived";
  public static final String PIPELINE_RESTORED = "pipeline.restored";

  private PipelineEventTypes() {}
}
