package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.repository.RoleRepository;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private RoleRepository roleRepository;
  @Mock private TenantMemberRepository memberRepository;

  private RoleService roleService;

  @BeforeEach
  void setUp() {
    roleService = new RoleService(roleRepository, memberRepository);
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void resolveAuthorization_returnsRoleAndPermissions() {
    TenantMember membership = new TenantMember(TENANT_ID, USER_ID, Role.EDITOR_ROLE_ID);
    Role editor =
        Role.builder()
            .id(Role.EDITOR_ROLE_ID)
            .name("editor")
            .permissions("[\"pipelines:read\", \"pipelines:write\"]")
            .isSystem(true)
            .build();

    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(membership));
    when(roleRepository.findById(Role.EDITOR_ROLE_ID)).thenReturn(Optional.of(editor));

    RoleService.UserAuthorization auth = roleService.resolveAuthorization(TENANT_ID, USER_ID);

    assertThat(auth.role()).isEqualTo(new UserRoleSummary(Role.EDITOR_ROLE_ID, "editor"));
    assertThat(auth.permissions()).containsExactly("pipelines:read", "pipelines:write");
  }

  @Test
  void resolveAuthorization_throwsWhenMembershipNotFound() {
    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void resolveAuthorization_throwsWhenRoleNotFound() {
    TenantMember membership = new TenantMember(TENANT_ID, USER_ID, Role.EDITOR_ROLE_ID);
    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(membership));
    when(roleRepository.findById(Role.EDITOR_ROLE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void listBuiltInRoles_returnsSystemRoles() {
    Role owner =
        Role.builder()
            .id(Role.OWNER_ROLE_ID)
            .name("owner")
            .permissions("[\"*\"]")
            .isSystem(true)
            .build();
    Role editor =
        Role.builder()
            .id(Role.EDITOR_ROLE_ID)
            .name("editor")
            .permissions("[]")
            .isSystem(true)
            .build();

    when(roleRepository.findSystemRoles()).thenReturn(List.of(owner, editor));

    var roles = roleService.listBuiltInRoles();

    assertThat(roles).hasSize(2);
    assertThat(roles.get(0).name()).isEqualTo("owner");
    assertThat(roles.get(1).name()).isEqualTo("editor");
  }

  @Test
  void assignRole_changesMembership() {
    TenantMember membership = TenantMember.createOwner(TENANT_ID, USER_ID);
    Role editor =
        Role.builder()
            .id(Role.EDITOR_ROLE_ID)
            .name("editor")
            .permissions("[\"pipelines:read\", \"pipelines:write\"]")
            .isSystem(true)
            .build();

    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(membership));
    when(roleRepository.findById(Role.EDITOR_ROLE_ID)).thenReturn(Optional.of(editor));
    when(memberRepository.countOwners(TENANT_ID, Role.OWNER_ROLE_ID)).thenReturn(2L);

    roleService.assignRole(USER_ID, Role.EDITOR_ROLE_ID);

    ArgumentCaptor<TenantMember> captor = ArgumentCaptor.forClass(TenantMember.class);
    verify(memberRepository).save(captor.capture());
    assertThat(captor.getValue().getRoleId()).isEqualTo(Role.EDITOR_ROLE_ID);
  }

  @Test
  void assignRole_rejectsRemovingLastOwner() {
    TenantMember membership = TenantMember.createOwner(TENANT_ID, USER_ID);
    Role editor =
        Role.builder()
            .id(Role.EDITOR_ROLE_ID)
            .name("editor")
            .permissions("[]")
            .isSystem(true)
            .build();

    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(membership));
    when(roleRepository.findById(Role.EDITOR_ROLE_ID)).thenReturn(Optional.of(editor));
    when(memberRepository.countOwners(TENANT_ID, Role.OWNER_ROLE_ID)).thenReturn(1L);

    assertThatThrownBy(() -> roleService.assignRole(USER_ID, Role.EDITOR_ROLE_ID))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void assignRole_allowsNonOwnerToChangeRole() {
    TenantMember membership = new TenantMember(TENANT_ID, USER_ID, Role.VIEWER_ROLE_ID);
    Role editor =
        Role.builder()
            .id(Role.EDITOR_ROLE_ID)
            .name("editor")
            .permissions("[]")
            .isSystem(true)
            .build();

    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(membership));
    when(roleRepository.findById(Role.EDITOR_ROLE_ID)).thenReturn(Optional.of(editor));

    roleService.assignRole(USER_ID, Role.EDITOR_ROLE_ID);

    verify(memberRepository).save(membership);
  }
}
