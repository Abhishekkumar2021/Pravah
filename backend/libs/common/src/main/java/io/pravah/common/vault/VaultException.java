package io.pravah.common.vault;

/** Raised when Vault is misconfigured or returns an error response. */
public class VaultException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public VaultException(String message) {
    super(message);
  }

  public VaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
