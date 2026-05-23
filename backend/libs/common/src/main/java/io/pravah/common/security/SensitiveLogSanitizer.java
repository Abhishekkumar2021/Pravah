package io.pravah.common.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Redacts sensitive values from maps before logging (e.g. runner JobSpec environment). */
public final class SensitiveLogSanitizer {

  private static final Set<String> SENSITIVE_KEY_FRAGMENTS =
      Set.of("password", "secret", "token", "api_key", "apikey", "authorization", "credential");

  private static final Pattern SENSITIVE_KEY_PATTERN =
      Pattern.compile(
          ".*(password|secret|token|api[_-]?key|authorization|credential).*",
          Pattern.CASE_INSENSITIVE);

  private SensitiveLogSanitizer() {}

  public static Map<String, String> redactEnvironment(Map<String, String> environment) {
    if (environment == null || environment.isEmpty()) {
      return Map.of();
    }
    Map<String, String> redacted = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      redacted.put(entry.getKey(), isSensitiveKey(entry.getKey()) ? "***" : entry.getValue());
    }
    return redacted;
  }

  public static boolean isSensitiveKey(String key) {
    if (key == null) {
      return false;
    }
    String lower = key.toLowerCase();
    for (String fragment : SENSITIVE_KEY_FRAGMENTS) {
      if (lower.contains(fragment)) {
        return true;
      }
    }
    return SENSITIVE_KEY_PATTERN.matcher(key).matches();
  }
}
