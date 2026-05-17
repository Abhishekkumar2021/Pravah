package io.pravah.common.exception;

/** Thrown when credentials are valid but the account email is not verified yet (US-10.01). */
public class EmailNotVerifiedException extends PravahException {

  private static final long serialVersionUID = 1L;

  public EmailNotVerifiedException() {
    super(
        "EMAIL_NOT_VERIFIED",
        "Email not verified. Check your inbox for the verification link or request a new one.");
  }

  @Override
  public int suggestedHttpStatus() {
    return 403;
  }
}
