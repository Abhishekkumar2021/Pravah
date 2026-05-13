package io.pravah.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Role")
class RoleTest {

  @Nested
  @DisplayName("system roles")
  class SystemRoles {

    @Test
    @DisplayName("should have predefined system role IDs")
    void shouldHaveSystemRoleIds() {
      assertThat(Role.OWNER_ROLE_ID)
          .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
      assertThat(Role.ADMIN_ROLE_ID)
          .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
      assertThat(Role.EDITOR_ROLE_ID)
          .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
      assertThat(Role.VIEWER_ROLE_ID)
          .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
    }

    @Test
    @DisplayName("should identify system roles correctly")
    void shouldIdentifySystemRoles() {
      Role systemRole =
          Role.builder()
              .id(Role.OWNER_ROLE_ID)
              .name("Owner")
              .description("Full access")
              .isSystem(true)
              .build();

      assertThat(systemRole.isSystem()).isTrue();
    }

    @Test
    @DisplayName("should prevent updating system role name")
    void shouldPreventUpdatingSystemRoleName() {
      Role systemRole =
          Role.builder()
              .id(Role.OWNER_ROLE_ID)
              .name("Owner")
              .description("Full access")
              .isSystem(true)
              .build();

      assertThatThrownBy(() -> systemRole.updateName("New Name"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot modify system role");
    }

    @Test
    @DisplayName("should prevent updating system role permissions")
    void shouldPreventUpdatingSystemRolePermissions() {
      Role systemRole =
          Role.builder()
              .id(Role.OWNER_ROLE_ID)
              .name("Owner")
              .description("Full access")
              .permissions("[\"*\"]")
              .isSystem(true)
              .build();

      assertThatThrownBy(() -> systemRole.updatePermissions("[\"read\"]"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot modify system role");
    }
  }

  @Nested
  @DisplayName("custom roles")
  class CustomRoles {

    @Test
    @DisplayName("should create custom role")
    void shouldCreateCustomRole() {
      UUID tenantId = UUID.randomUUID();
      Role role =
          Role.builder()
              .tenantId(tenantId)
              .name("Developer")
              .description("Can manage pipelines")
              .permissions("[\"pipeline:read\", \"pipeline:write\", \"run:create\"]")
              .build();

      assertThat(role.getId()).isNotNull();
      assertThat(role.getTenantId()).isEqualTo(tenantId);
      assertThat(role.getName()).isEqualTo("Developer");
      assertThat(role.isSystem()).isFalse();
      assertThat(role.getPermissions()).contains("pipeline:read");
    }

    @Test
    @DisplayName("should allow updating custom role name")
    void shouldAllowUpdatingCustomRoleName() {
      Role role =
          Role.builder()
              .tenantId(UUID.randomUUID())
              .name("Developer")
              .description("Can manage pipelines")
              .build();

      role.updateName("Senior Developer");

      assertThat(role.getName()).isEqualTo("Senior Developer");
    }

    @Test
    @DisplayName("should allow updating custom role permissions")
    void shouldAllowUpdatingCustomRolePermissions() {
      Role role =
          Role.builder()
              .tenantId(UUID.randomUUID())
              .name("Developer")
              .permissions("[\"pipeline:read\"]")
              .build();

      role.updatePermissions("[\"pipeline:read\", \"pipeline:write\"]");

      assertThat(role.getPermissions()).contains("pipeline:read").contains("pipeline:write");
    }
  }
}
