package io.pravah.spring.security;

import io.pravah.common.security.PermissionMatcher;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Method-security helper for {@code @PreAuthorize("@permissionChecker.has('pipelines:write')")}.
 *
 * <p>Denies access by default when no permissions are present in the token.
 */
@Component("permissionChecker")
public class PermissionChecker {

  public boolean has(String permission) {
    Collection<String> granted = currentPermissions();
    if (granted.isEmpty()) {
      return false;
    }
    return PermissionMatcher.isGranted(permission, granted);
  }

  public boolean hasAny(String... permissions) {
    for (String permission : permissions) {
      if (has(permission)) {
        return true;
      }
    }
    return false;
  }

  private static Collection<String> currentPermissions() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(auth -> !auth.startsWith("ROLE_"))
        .collect(Collectors.toSet());
  }
}
