package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SecurityAuthoritiesTest {

  @Test
  void fromJwtPermissions_alwaysIncludesRoleUser() {
    var authorities = SecurityAuthorities.fromJwtPermissions(List.of("pipelines:read"));

    assertThat(authorities)
        .anyMatch(a -> a.equals(new SimpleGrantedAuthority("ROLE_USER")));
    assertThat(authorities)
        .anyMatch(a -> a.equals(new SimpleGrantedAuthority("pipelines:read")));
  }

  @Test
  void fromJwtPermissions_skipsBlankEntries() {
    var authorities =
        SecurityAuthorities.fromJwtPermissions(java.util.Arrays.asList(" ", null, "users:read"));

    assertThat(authorities).hasSize(2);
  }
}
