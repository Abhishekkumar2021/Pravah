package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionCheckerTest {

  private final PermissionChecker checker = new PermissionChecker();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void has_grantsWhenPermissionPresent() {
    setAuthorities("users:write");
    assertThat(checker.has("users:write")).isTrue();
    assertThat(checker.has("users:read")).isFalse();
  }

  @Test
  void has_wildcardGrantsResource() {
    setAuthorities("users:*");
    assertThat(checker.has("users:write")).isTrue();
  }

  @Test
  void has_globalWildcardGrantsAll() {
    setAuthorities("*");
    assertThat(checker.has("users:write")).isTrue();
    assertThat(checker.has("pipelines:read")).isTrue();
  }

  @Test
  void has_deniesWhenNoPermissions() {
    setAuthoritiesWithRoleOnly();
    assertThat(checker.has("users:write")).isFalse();
  }

  @Test
  void has_deniesWhenNoAuthentication() {
    assertThat(checker.has("users:write")).isFalse();
  }

  @Test
  void hasAny_grantsWhenAnyPermissionMatches() {
    setAuthorities("pipelines:read");
    assertThat(checker.hasAny("users:write", "pipelines:read")).isTrue();
  }

  @Test
  void hasAny_deniesWhenNoPermissionMatches() {
    setAuthorities("pipelines:read");
    assertThat(checker.hasAny("users:write", "users:read")).isFalse();
  }

  private static void setAuthorities(String... permissions) {
    var authorities =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(new SimpleGrantedAuthority("ROLE_USER")),
                List.of(permissions).stream().map(SimpleGrantedAuthority::new))
            .toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user", null, authorities));
  }

  private static void setAuthoritiesWithRoleOnly() {
    var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user", null, authorities));
  }
}
