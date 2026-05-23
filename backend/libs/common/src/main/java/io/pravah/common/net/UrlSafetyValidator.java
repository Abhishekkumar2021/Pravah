package io.pravah.common.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks SSRF-prone targets (loopback, RFC1918, link-local, metadata endpoints) for outbound HTTP
 * and JDBC connection tests.
 */
public final class UrlSafetyValidator {

  private static final Set<String> BLOCKED_HOSTS =
      Set.of("localhost", "metadata.google.internal", "metadata.goog");

  private UrlSafetyValidator() {}

  /** Validates an HTTP(S) URL before the platform calls it (webhooks, Slack, etc.). */
  public static void validateHttpUrlForOutboundRequest(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("URL is required");
    }
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid URL: " + e.getMessage(), e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
      throw new IllegalArgumentException("Only http and https URLs are allowed");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("URL must include a host");
    }
    validateResolvedHost(host);
  }

  /** Validates host portion of a JDBC URL or postgres host field. */
  public static void validateJdbcTarget(String jdbcUrlOrHost) {
    if (jdbcUrlOrHost == null || jdbcUrlOrHost.isBlank()) {
      throw new IllegalArgumentException("JDBC target is required");
    }
    String host = extractJdbcHost(jdbcUrlOrHost.trim());
    validateResolvedHost(host);
  }

  private static String extractJdbcHost(String jdbcUrlOrHost) {
    if (!jdbcUrlOrHost.startsWith("jdbc:")) {
      return jdbcUrlOrHost;
    }
    int schemeEnd = jdbcUrlOrHost.indexOf("://");
    if (schemeEnd < 0) {
      throw new IllegalArgumentException("Invalid JDBC URL");
    }
    String remainder = jdbcUrlOrHost.substring(schemeEnd + 3);
    int slash = remainder.indexOf('/');
    String authority = slash >= 0 ? remainder.substring(0, slash) : remainder;
    int at = authority.lastIndexOf('@');
    if (at >= 0) {
      authority = authority.substring(at + 1);
    }
    int colon = authority.indexOf(':');
    String host = colon >= 0 ? authority.substring(0, colon) : authority;
    if (host.isBlank()) {
      throw new IllegalArgumentException("Invalid JDBC URL");
    }
    return host;
  }

  private static void validateResolvedHost(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    if (BLOCKED_HOSTS.contains(normalized)) {
      throw new IllegalArgumentException("Connection to host '%s' is not allowed".formatted(host));
    }
    if (normalized.endsWith(".local") || normalized.endsWith(".internal")) {
      throw new IllegalArgumentException("Connection to host '%s' is not allowed".formatted(host));
    }
    try {
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (isBlockedAddress(address)) {
          throw new IllegalArgumentException(
              "Connection to host '%s' resolves to a non-routable address".formatted(host));
        }
      }
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unknown host: " + host, e);
    }
  }

  private static boolean isBlockedAddress(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] octets = address.getAddress();
    if (octets.length == 4) {
      int b0 = octets[0] & 0xff;
      int b1 = octets[1] & 0xff;
      if (b0 == 0) {
        return true;
      }
      if (b0 == 10) {
        return true;
      }
      if (b0 == 172 && b1 >= 16 && b1 <= 31) {
        return true;
      }
      if (b0 == 192 && b1 == 168) {
        return true;
      }
      if (b0 == 169 && b1 == 254) {
        return true;
      }
    }
    return false;
  }
}
