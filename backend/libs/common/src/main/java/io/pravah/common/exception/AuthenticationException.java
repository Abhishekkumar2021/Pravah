package io.pravah.common.exception;

/** Thrown when credentials are invalid or the account cannot authenticate. */
public class AuthenticationException extends PravahException {

  private static final long serialVersionUID = 1L;

  public AuthenticationException(String message) {
    super("AUTHENTICATION_FAILED", message);
  }

  @Override
  public int suggestedHttpStatus() {
    return 401;
  }
}
