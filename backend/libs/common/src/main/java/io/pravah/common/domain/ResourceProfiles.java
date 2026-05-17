package io.pravah.common.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Predefined resource profiles for container stages (US-02.08).
 *
 * <p>Profiles provide sensible defaults for common workload sizes, reducing configuration overhead.
 */
public final class ResourceProfiles {

  /** Resolved memory and CPU limits for a profile. */
  public record ResourceLimits(String memory, String cpus) {}

  private static final ResourceLimits SMALL = new ResourceLimits("512Mi", "0.5");
  private static final ResourceLimits MEDIUM = new ResourceLimits("2Gi", "2");
  private static final ResourceLimits LARGE = new ResourceLimits("8Gi", "4");
  private static final ResourceLimits XLARGE = new ResourceLimits("16Gi", "8");

  private ResourceProfiles() {}

  /**
   * Resolves a profile name to resource limits.
   *
   * @param profile profile name (small, medium, large, xlarge)
   * @return resource limits
   * @throws IllegalArgumentException if profile is unknown
   */
  public static ResourceLimits resolve(String profile) {
    if (profile == null || profile.isBlank()) {
      throw new IllegalArgumentException("Profile must not be blank");
    }
    String normalized = profile.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "small", "s" -> SMALL;
      case "medium", "m" -> MEDIUM;
      case "large", "l" -> LARGE;
      case "xlarge", "xl", "x-large" -> XLARGE;
      default -> throw new IllegalArgumentException("Unknown resource profile: " + profile);
    };
  }

  /**
   * Checks if a profile name is valid.
   *
   * @param profile profile name
   * @return true if valid
   */
  public static boolean isValidProfile(String profile) {
    if (profile == null || profile.isBlank()) {
      return false;
    }
    String normalized = profile.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "small", "s", "medium", "m", "large", "l", "xlarge", "xl", "x-large" -> true;
      default -> false;
    };
  }

  /**
   * Merges explicit resource values with profile defaults.
   *
   * <p>Explicit values override profile defaults. If no profile is specified and no explicit values
   * are provided, returns null for both memory and cpus (no limits).
   *
   * @param resources raw resources map from stage config
   * @return resolved limits (may contain nulls if no profile and no explicit values)
   */
  public static ResourceLimits mergeWithProfile(Map<?, ?> resources) {
    if (resources == null || resources.isEmpty()) {
      return new ResourceLimits(null, null);
    }

    String memory = null;
    String cpus = null;

    Object profileObj = resources.get("profile");
    if (profileObj != null && !profileObj.toString().isBlank()) {
      ResourceLimits profileLimits = resolve(profileObj.toString());
      memory = profileLimits.memory();
      cpus = profileLimits.cpus();
    }

    Object memoryObj = resources.get("memory");
    if (memoryObj != null && !memoryObj.toString().isBlank()) {
      memory = memoryObj.toString().trim();
    }

    Object cpusObj = resources.get("cpus");
    if (cpusObj != null && !cpusObj.toString().isBlank()) {
      cpus = cpusObj.toString().trim();
    }

    return new ResourceLimits(memory, cpus);
  }
}
