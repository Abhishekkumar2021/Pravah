package io.pravah.tenant.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RolePermissionsTest {

  @Test
  void parse_blankReturnsEmptyList() {
    assertThat(RolePermissions.parse(null)).isEmpty();
    assertThat(RolePermissions.parse("  ")).isEmpty();
  }

  @Test
  void parse_validJson_returnsPermissions() {
    assertThat(RolePermissions.parse("[\"users:read\", \"pipelines:*\"]"))
        .containsExactly("users:read", "pipelines:*");
  }

  @Test
  void parse_invalidJson_throws() {
    assertThatThrownBy(() -> RolePermissions.parse("not-json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid permissions JSON");
  }

  @Test
  void toJson_serializesPermissions() {
    assertThat(RolePermissions.toJson(List.of("users:read"))).isEqualTo("[\"users:read\"]");
  }

  @Test
  void toJson_rejectsEmptyList() {
    assertThatThrownBy(() -> RolePermissions.toJson(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
