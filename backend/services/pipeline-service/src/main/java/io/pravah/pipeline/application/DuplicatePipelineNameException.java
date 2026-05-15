package io.pravah.pipeline.application;

/** Thrown when {@code UNIQUE(project_id, name)} is violated. */
public class DuplicatePipelineNameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicatePipelineNameException(String message, Throwable cause) {
    super(message, cause);
  }
}
