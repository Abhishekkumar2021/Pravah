package io.pravah.common.security;

import java.util.regex.Pattern;

/** Validates tenant secret names referenced in remote job specs. */
public final class SecretNameValidator {

  private static final Pattern TENANT_SECRET_NAME =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]{0,127}$");

  private SecretNameValidator() {}

  public static void requireValidTenantSecretName(String secretName) {
    if (secretName == null || secretName.isBlank()) {
      throw new IllegalArgumentException("Secret name is required");
    }
    if (secretName.startsWith("__")) {
      throw new IllegalArgumentException("Reserved secret name: " + secretName);
    }
    if (!TENANT_SECRET_NAME.matcher(secretName).matches()) {
      throw new IllegalArgumentException("Invalid secret name: " + secretName);
    }
  }
}
