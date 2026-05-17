package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceProfilesTest {

  @ParameterizedTest
  @ValueSource(strings = {"small", "Small", "SMALL", "s", "S"})
  void resolve_small_returnsSmallLimits(String profile) {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.resolve(profile);
    assertThat(limits.memory()).isEqualTo("512Mi");
    assertThat(limits.cpus()).isEqualTo("0.5");
  }

  @ParameterizedTest
  @ValueSource(strings = {"medium", "Medium", "m", "M"})
  void resolve_medium_returnsMediumLimits(String profile) {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.resolve(profile);
    assertThat(limits.memory()).isEqualTo("2Gi");
    assertThat(limits.cpus()).isEqualTo("2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"large", "Large", "l", "L"})
  void resolve_large_returnsLargeLimits(String profile) {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.resolve(profile);
    assertThat(limits.memory()).isEqualTo("8Gi");
    assertThat(limits.cpus()).isEqualTo("4");
  }

  @ParameterizedTest
  @ValueSource(strings = {"xlarge", "XLarge", "xl", "XL", "x-large"})
  void resolve_xlarge_returnsXLargeLimits(String profile) {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.resolve(profile);
    assertThat(limits.memory()).isEqualTo("16Gi");
    assertThat(limits.cpus()).isEqualTo("8");
  }

  @Test
  void resolve_unknownProfile_throws() {
    assertThatThrownBy(() -> ResourceProfiles.resolve("tiny"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown resource profile");
  }

  @Test
  void resolve_blank_throws() {
    assertThatThrownBy(() -> ResourceProfiles.resolve("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @ParameterizedTest
  @ValueSource(strings = {"small", "medium", "large", "xlarge", "s", "m", "l", "xl"})
  void isValidProfile_validProfiles_returnsTrue(String profile) {
    assertThat(ResourceProfiles.isValidProfile(profile)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"tiny", "huge", "extra-small", ""})
  void isValidProfile_invalidProfiles_returnsFalse(String profile) {
    assertThat(ResourceProfiles.isValidProfile(profile)).isFalse();
  }

  @Test
  void isValidProfile_null_returnsFalse() {
    assertThat(ResourceProfiles.isValidProfile(null)).isFalse();
  }

  @Test
  void mergeWithProfile_nullResources_returnsNullLimits() {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.mergeWithProfile(null);
    assertThat(limits.memory()).isNull();
    assertThat(limits.cpus()).isNull();
  }

  @Test
  void mergeWithProfile_emptyResources_returnsNullLimits() {
    ResourceProfiles.ResourceLimits limits = ResourceProfiles.mergeWithProfile(Map.of());
    assertThat(limits.memory()).isNull();
    assertThat(limits.cpus()).isNull();
  }

  @Test
  void mergeWithProfile_profileOnly_returnsProfileLimits() {
    ResourceProfiles.ResourceLimits limits =
        ResourceProfiles.mergeWithProfile(Map.of("profile", "medium"));
    assertThat(limits.memory()).isEqualTo("2Gi");
    assertThat(limits.cpus()).isEqualTo("2");
  }

  @Test
  void mergeWithProfile_explicitValues_overrideProfile() {
    ResourceProfiles.ResourceLimits limits =
        ResourceProfiles.mergeWithProfile(Map.of("profile", "small", "memory", "1Gi", "cpus", "1"));
    assertThat(limits.memory()).isEqualTo("1Gi");
    assertThat(limits.cpus()).isEqualTo("1");
  }

  @Test
  void mergeWithProfile_partialOverride_mergesCorrectly() {
    ResourceProfiles.ResourceLimits limits =
        ResourceProfiles.mergeWithProfile(Map.of("profile", "large", "memory", "4Gi"));
    assertThat(limits.memory()).isEqualTo("4Gi");
    assertThat(limits.cpus()).isEqualTo("4");
  }

  @Test
  void mergeWithProfile_explicitOnlyNoProfile_returnsExplicitValues() {
    ResourceProfiles.ResourceLimits limits =
        ResourceProfiles.mergeWithProfile(Map.of("memory", "256Mi", "cpus", "0.25"));
    assertThat(limits.memory()).isEqualTo("256Mi");
    assertThat(limits.cpus()).isEqualTo("0.25");
  }
}
