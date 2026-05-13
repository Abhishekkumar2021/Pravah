package io.pravah.common.exception;

/**
 * Base exception for all Pravah domain exceptions.
 *
 * <p>All domain-specific exceptions should extend this class. This allows for consistent error
 * handling across services.
 */
public abstract class PravahException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;

  protected PravahException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  protected PravahException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Machine-readable error code for client handling. Format: DOMAIN_ACTION (e.g.,
   * PIPELINE_NOT_FOUND, RUN_INVALID_STATE)
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * HTTP status code suggestion for REST APIs.
   *
   * @return suggested HTTP status code
   */
  public abstract int suggestedHttpStatus();
}
