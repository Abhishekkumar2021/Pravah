package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemainingDomainIdsTest {

  @Test
  void executionId_roundTrips() {
    UUID uuid = UUID.randomUUID();
    assertThat(ExecutionId.of(uuid).value()).isEqualTo(uuid);
    assertThat(ExecutionId.of(uuid.toString()).value()).isEqualTo(uuid);
  }

  @Test
  void projectId_roundTrips() {
    UUID uuid = UUID.randomUUID();
    assertThat(ProjectId.of(uuid).value()).isEqualTo(uuid);
    assertThat(ProjectId.of(uuid.toString()).value()).isEqualTo(uuid);
    assertThat(ProjectId.generate().value()).isNotNull();
  }

  @Test
  void projectId_rejectsInvalidString() {
    assertThatThrownBy(() -> ProjectId.of("bad")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void executionId_rejectsNullUuid() {
    assertThatThrownBy(() -> ExecutionId.of((UUID) null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void roleId_generateIsUnique() {
    assertThat(RoleId.generate()).isNotEqualTo(RoleId.generate());
  }

  @Test
  void roleId_systemRoles_areRecognized() {
    assertThat(RoleId.OWNER.isSystemRole()).isTrue();
    assertThat(RoleId.ADMIN.isSystemRole()).isTrue();
    assertThat(RoleId.generate().isSystemRole()).isFalse();
    assertThat(RoleId.of(UUID.randomUUID()).toString()).isNotBlank();
  }

  @Test
  void teamId_equalsByValue() {
    UUID uuid = UUID.randomUUID();
    assertThat(TeamId.of(uuid)).isEqualTo(TeamId.of(uuid));
  }
}
