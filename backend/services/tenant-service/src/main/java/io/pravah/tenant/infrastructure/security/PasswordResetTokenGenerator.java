package io.pravah.tenant.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates URL-safe password reset tokens. */
public final class PasswordResetTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private PasswordResetTokenGenerator() {}

  public static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
