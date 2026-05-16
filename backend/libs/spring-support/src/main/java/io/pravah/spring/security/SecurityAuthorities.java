package io.pravah.spring.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Builds Spring Security authorities from JWT permission claims. */
public final class SecurityAuthorities {

  private static final String ROLE_USER = "ROLE_USER";

  private SecurityAuthorities() {}

  public static List<GrantedAuthority> fromJwtPermissions(Collection<String> permissions) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority(ROLE_USER));
    if (permissions != null) {
      for (String permission : permissions) {
        if (permission != null && !permission.isBlank()) {
          authorities.add(new SimpleGrantedAuthority(permission));
        }
      }
    }
    return authorities;
  }
}
