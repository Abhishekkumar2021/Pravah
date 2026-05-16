package io.pravah.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantMemberTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void createOwner_usesOwnerRole() {
    TenantMember member = TenantMember.createOwner(TENANT_ID, USER_ID);

    assertThat(member.getRoleId()).isEqualTo(Role.OWNER_ROLE_ID);
    assertThat(member.isOwner()).isTrue();
    assertThat(member.getJoinedAt()).isNotNull();
  }

  @Test
  void factoryMethods_assignExpectedRoles() {
    assertThat(TenantMember.createAdmin(TENANT_ID, USER_ID).getRoleId())
        .isEqualTo(Role.ADMIN_ROLE_ID);
    assertThat(TenantMember.createEditor(TENANT_ID, USER_ID).getRoleId())
        .isEqualTo(Role.EDITOR_ROLE_ID);
    assertThat(TenantMember.createViewer(TENANT_ID, USER_ID).getRoleId())
        .isEqualTo(Role.VIEWER_ROLE_ID);
  }

  @Test
  void changeRole_updatesRoleId() {
    TenantMember member = TenantMember.createViewer(TENANT_ID, USER_ID);

    member.changeRole(Role.EDITOR_ROLE_ID);

    assertThat(member.getRoleId()).isEqualTo(Role.EDITOR_ROLE_ID);
    assertThat(member.isOwner()).isFalse();
  }

  @Test
  void equals_matchesByTenantAndUserOnly() {
    TenantMember a = TenantMember.createViewer(TENANT_ID, USER_ID);
    TenantMember b = new TenantMember(TENANT_ID, USER_ID, Role.ADMIN_ROLE_ID);

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.isAdmin()).isFalse();
    assertThat(TenantMember.createAdmin(TENANT_ID, USER_ID).isAdmin()).isTrue();
  }

  @Test
  void tenantMemberId_equalsByTenantAndUser() {
    var id1 = new TenantMemberId(TENANT_ID, USER_ID);
    var id2 = new TenantMemberId(TENANT_ID, USER_ID);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
