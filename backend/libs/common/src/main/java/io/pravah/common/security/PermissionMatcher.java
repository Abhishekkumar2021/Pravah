package io.pravah.common.security;

import java.util.Collection;

/**
 * Matches required permissions against a granted set (supports {@code *} and {@code resource:*}).
 */
public final class PermissionMatcher {

  private PermissionMatcher() {}

  /**
   * Returns whether {@code required} is satisfied by any entry in {@code granted}.
   *
   * <p>Supports exact match, global wildcard ({@code *}), and resource wildcard ({@code
   * pipelines:*}).
   */
  public static boolean isGranted(String required, Collection<String> granted) {
    if (required == null || required.isBlank()) {
      return false;
    }
    if (granted == null || granted.isEmpty()) {
      return false;
    }
    for (String grant : granted) {
      if (grant == null || grant.isBlank()) {
        continue;
      }
      if ("*".equals(grant) || grant.equals(required)) {
        return true;
      }
      if (grant.endsWith(":*")) {
        String prefix = grant.substring(0, grant.length() - 2);
        if (required.startsWith(prefix + ":")) {
          return true;
        }
      }
    }
    return false;
  }
}
