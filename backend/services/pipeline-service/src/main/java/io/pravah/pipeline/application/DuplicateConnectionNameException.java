package io.pravah.pipeline.application;

public class DuplicateConnectionNameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateConnectionNameException(String message, Throwable cause) {
    super(message, cause);
  }
}
