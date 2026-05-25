package io.pravah.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionMatcherTest {

  @Test
  void isGranted_exactMatch() {
    assertThat(PermissionMatcher.isGranted("pipelines:read", List.of("pipelines:read"))).isTrue();
  }

  @Test
  void isGranted_globalWildcard() {
    assertThat(PermissionMatcher.isGranted("users:write", List.of("*"))).isTrue();
  }

  @Test
  void isGranted_resourceWildcard() {
    assertThat(PermissionMatcher.isGranted("pipelines:write", List.of("pipelines:*"))).isTrue();
    assertThat(PermissionMatcher.isGranted("executions:read", List.of("pipelines:*"))).isFalse();
  }

  @Test
  void isGranted_deniedWhenNotGranted() {
    assertThat(PermissionMatcher.isGranted("users:write", List.of("pipelines:read"))).isFalse();
  }

  @Test
  void isGranted_nullRequired_returnsFalse() {
    assertThat(PermissionMatcher.isGranted(null, List.of("*"))).isFalse();
  }

  @Test
  void isGranted_emptyRequired_returnsFalse() {
    assertThat(PermissionMatcher.isGranted("", List.of("*"))).isFalse();
    assertThat(PermissionMatcher.isGranted("   ", List.of("*"))).isFalse();
  }

  @Test
  void isGranted_nullGranted_returnsFalse() {
    assertThat(PermissionMatcher.isGranted("users:write", null)).isFalse();
  }

  @Test
  void isGranted_emptyGranted_returnsFalse() {
    assertThat(PermissionMatcher.isGranted("users:write", Collections.emptyList())).isFalse();
  }

  @Test
  void isGranted_nullInGrantedList_skipped() {
    assertThat(PermissionMatcher.isGranted("users:write", List.of("users:write"))).isTrue();
  }

  @Test
  void isGranted_multipleGrants_matchesAny() {
    var grants = List.of("pipelines:read", "users:write", "executions:*");
    assertThat(PermissionMatcher.isGranted("users:write", grants)).isTrue();
    assertThat(PermissionMatcher.isGranted("executions:cancel", grants)).isTrue();
    assertThat(PermissionMatcher.isGranted("tenants:read", grants)).isFalse();
  }

  @Test
  void isGranted_caseSensitive() {
    assertThat(PermissionMatcher.isGranted("Users:Write", List.of("users:write"))).isFalse();
    assertThat(PermissionMatcher.isGranted("USERS:WRITE", List.of("users:write"))).isFalse();
  }

  @Test
  void isGranted_partialResourceWildcardDoesNotMatch() {
    assertThat(PermissionMatcher.isGranted("pipelinesExtra:read", List.of("pipelines:*")))
        .isFalse();
  }
}
