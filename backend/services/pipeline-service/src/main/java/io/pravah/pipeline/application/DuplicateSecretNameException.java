package io.pravah.pipeline.application;

/** Thrown when attempting to create a secret with a name that already exists in the tenant. */
public class DuplicateSecretNameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateSecretNameException(String message, Throwable cause) {
    super(message, cause);
  }
}
