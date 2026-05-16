package io.pravah.tenant.infrastructure.security;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Generates API keys in {@code prv_1_<64-hex>} format (ADR-009). */
public final class ApiTokenGenerator {

  public static final String PREFIX = "prv_1_";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int RANDOM_BYTES = 32;

  private ApiTokenGenerator() {}

  public static String generate() {
    byte[] bytes = new byte[RANDOM_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return PREFIX + HexFormat.of().formatHex(bytes);
  }

  public static boolean isApiToken(String bearerValue) {
    return bearerValue != null && bearerValue.startsWith(PREFIX);
  }
}
