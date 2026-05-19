package io.pravah.scheduler.infrastructure.client;

import java.io.Serial;

/**
 * Thrown when an upstream service is unavailable (circuit breaker open or exhausted retries).
 *
 * <p>This exception should be translated to HTTP 503 by the exception handler.
 */
public class ServiceUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ServiceUnavailableException(String message) {
    super(message);
  }

  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
