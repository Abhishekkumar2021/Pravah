package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.RoleResponse;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.application.security.RolePermissions;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.repository.RoleRepository;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Built-in roles and tenant membership role assignments (US-10.05). */
@Service
@Transactional
public class RoleService {

  private static final Logger log = LoggerFactory.getLogger(RoleService.class);

  private final RoleRepository roleRepository;
  private final TenantMemberRepository memberRepository;

  public RoleService(RoleRepository roleRepository, TenantMemberRepository memberRepository) {
    this.roleRepository = roleRepository;
    this.memberRepository = memberRepository;
  }

  /** Resolved role and permissions for JWT issuance. */
  public record UserAuthorization(UserRoleSummary role, List<String> permissions) {}

  @Transactional(readOnly = true)
  public List<RoleResponse> listBuiltInRoles() {
    return roleRepository.findSystemRoles().stream().map(RoleResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public UserAuthorization resolveAuthorization(UUID tenantId, UUID userId) {
    TenantMember membership =
        memberRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new EntityNotFoundException("TenantMember", userId));

    Role role =
        roleRepository
            .findById(membership.getRoleId())
            .orElseThrow(() -> new EntityNotFoundException("Role", membership.getRoleId()));

    return new UserAuthorization(
        new UserRoleSummary(role.getId(), role.getName()),
        RolePermissions.parse(role.getPermissions()));
  }

  @Transactional(readOnly = true)
  public UserRoleSummary getUserRoleSummary(UUID tenantId, UUID userId) {
    return resolveAuthorization(tenantId, userId).role();
  }

  public void assignRole(UUID userId, UUID roleId) {
    UUID tenantId = requireTenantContext();

    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new EntityNotFoundException("Role", roleId));

    if (!role.isSystem() && !tenantId.equals(role.getTenantId())) {
      throw ValidationException.of("roleId", "Role is not available for this tenant");
    }

    TenantMember membership =
        memberRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new EntityNotFoundException("TenantMember", userId));

    if (membership.isOwner() && !Role.OWNER_ROLE_ID.equals(roleId)) {
      long ownerCount = memberRepository.countOwners(tenantId, Role.OWNER_ROLE_ID);
      if (ownerCount <= 1) {
        throw ValidationException.of(
            "roleId", "Cannot change role: tenant must have at least one owner");
      }
    }

    UUID previousRoleId = membership.getRoleId();
    membership.changeRole(roleId);
    memberRepository.save(membership);

    log.info(
        "Assigned role to user",
        kv("user_id", userId),
        kv("tenant_id", tenantId),
        kv("previous_role_id", previousRoleId),
        kv("role_id", roleId));
  }

  private UUID requireTenantContext() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }
}
