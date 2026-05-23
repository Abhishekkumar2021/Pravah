package io.pravah.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** IPv4/IPv6 CIDR and exact-IP matching for gateway allowlists. */
public final class IpCidrMatcher {

  private IpCidrMatcher() {}

  public static boolean matches(String clientIp, String rule) {
    if (clientIp == null || clientIp.isBlank() || rule == null || rule.isBlank()) {
      return false;
    }
    String trimmedRule = rule.trim();
    if (!trimmedRule.contains("/")) {
      return normalizeHost(clientIp).equals(normalizeHost(trimmedRule));
    }
    String[] parts = trimmedRule.split("/", 2);
    if (parts.length != 2) {
      return false;
    }
    try {
      InetAddress network = InetAddress.getByName(parts[0].trim());
      int prefix = Integer.parseInt(parts[1].trim());
      InetAddress address = InetAddress.getByName(clientIp.trim());
      if (network.getAddress().length != address.getAddress().length) {
        return false;
      }
      byte[] networkBytes = network.getAddress();
      byte[] addressBytes = address.getAddress();
      int maxPrefix = networkBytes.length * 8;
      if (prefix < 0 || prefix > maxPrefix) {
        return false;
      }
      int fullBytes = prefix / 8;
      int remainingBits = prefix % 8;
      for (int i = 0; i < fullBytes; i++) {
        if (networkBytes[i] != addressBytes[i]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (8 - remainingBits);
      return (networkBytes[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
    } catch (UnknownHostException | NumberFormatException e) {
      return false;
    }
  }

  private static String normalizeHost(String host) {
    try {
      return InetAddress.getByName(host.trim()).getHostAddress();
    } catch (UnknownHostException e) {
      return host.trim().toLowerCase();
    }
  }
}
