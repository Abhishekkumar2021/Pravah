package io.pravah.common.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses container resource limits for Docker/Kubernetes-style quantities (US-02.17).
 *
 * <p>Supports binary (Ki, Mi, Gi) and decimal (K, M, G) suffixes per Kubernetes conventions.
 */
public final class ContainerResourceParser {

  private static final Pattern MEMORY =
      Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)([KMGTP]i?)?$", Pattern.CASE_INSENSITIVE);

  private ContainerResourceParser() {}

  /**
   * Parses a memory quantity to Docker's byte format (e.g. {@code 512m}, {@code 1g}).
   *
   * @throws IllegalArgumentException if the value cannot be parsed
   */
  public static String toDockerMemoryLimit(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Memory limit must not be blank");
    }
    String trimmed = raw.trim();
    Matcher matcher = MEMORY.matcher(trimmed);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid memory quantity: " + raw);
    }
    double amount = Double.parseDouble(matcher.group(1));
    String suffix = matcher.group(2);
    if (suffix == null || suffix.isEmpty()) {
      return String.valueOf((long) amount);
    }
    long multiplier = memoryMultiplier(suffix);
    long bytes = (long) (amount * multiplier);
    if (bytes <= 0) {
      throw new IllegalArgumentException("Memory limit must be positive: " + raw);
    }
    return formatDockerBytes(bytes);
  }

  /**
   * Validates a CPU limit string (positive decimal cores).
   *
   * @return normalized string suitable for {@code docker run --cpus}
   */
  public static String toDockerCpuLimit(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("CPU limit must not be blank");
    }
    String trimmed = raw.trim();
    double value;
    try {
      value = Double.parseDouble(trimmed);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("CPU limit must be a positive number: " + raw, e);
    }
    if (value <= 0) {
      throw new IllegalArgumentException("CPU limit must be positive: " + raw);
    }
    return trimmed;
  }

  private static long memoryMultiplier(String suffix) {
    String normalized = suffix.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "k" -> 1_000L;
      case "m" -> 1_000_000L;
      case "g" -> 1_000_000_000L;
      case "t" -> 1_000_000_000_000L;
      case "p" -> 1_000_000_000_000_000L;
      case "ki" -> 1_024L;
      case "mi" -> 1_024L * 1_024L;
      case "gi" -> 1_024L * 1_024L * 1_024L;
      case "ti" -> 1_024L * 1_024L * 1_024L * 1_024L;
      case "pi" -> 1_024L * 1_024L * 1_024L * 1_024L * 1_024L;
      default -> throw new IllegalArgumentException("Unknown memory suffix: " + suffix);
    };
  }

  private static String formatDockerBytes(long bytes) {
    if (bytes >= 1_073_741_824L && bytes % 1_073_741_824L == 0) {
      return (bytes / 1_073_741_824L) + "g";
    }
    if (bytes >= 1_048_576L && bytes % 1_048_576L == 0) {
      return (bytes / 1_048_576L) + "m";
    }
    if (bytes >= 1_024L && bytes % 1_024L == 0) {
      return (bytes / 1_024L) + "k";
    }
    return String.valueOf(bytes);
  }
}
