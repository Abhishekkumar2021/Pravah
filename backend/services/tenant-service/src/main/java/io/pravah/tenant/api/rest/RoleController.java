package io.pravah.tenant.api.rest;

import io.pravah.tenant.application.dto.RoleResponse;
import io.pravah.tenant.application.service.RoleService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists built-in tenant roles (US-10.05).
 *
 * <p>Accessible to any authenticated user with role read permissions. Viewers can see available
 * roles even if they cannot assign them.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('roles:read', 'roles:*', 'users:read', 'users:*')")
  public ResponseEntity<List<RoleResponse>> listBuiltInRoles() {
    return ResponseEntity.ok(roleService.listBuiltInRoles());
  }
}
