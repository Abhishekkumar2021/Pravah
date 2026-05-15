package io.pravah.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("User")
class UserTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Nested
  @DisplayName("creation")
  class Creation {

    @Test
    @DisplayName("should create user with all required fields")
    void shouldCreateWithRequiredFields() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test User")
              .status(User.Status.ACTIVE)
              .build();

      assertThat(user.getId()).isNotNull();
      assertThat(user.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(user.getEmail()).isEqualTo("test@example.com");
      assertThat(user.getName()).isEqualTo("Test User");
      assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
      assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should default to PENDING status")
    void shouldDefaultToPendingStatus() {
      User user =
          User.builder().tenantId(TENANT_ID).email("test@example.com").name("Test User").build();

      assertThat(user.getStatus()).isEqualTo(User.Status.PENDING);
    }

    @Test
    @DisplayName("should throw when tenantId is null")
    void shouldThrowWhenTenantIdIsNull() {
      assertThatThrownBy(() -> User.builder().email("test@example.com").name("Test User").build())
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("status transitions")
  class StatusTransitions {

    @Test
    @DisplayName("should activate pending user")
    void shouldActivatePendingUser() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.PENDING)
              .build();

      user.activate();

      assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
    }

    @Test
    @DisplayName("should deactivate active user")
    void shouldDeactivateActiveUser() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.ACTIVE)
              .build();

      user.deactivate();

      assertThat(user.getStatus()).isEqualTo(User.Status.INACTIVE);
    }

    @Test
    @DisplayName("should lock user")
    void shouldLockUser() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.ACTIVE)
              .build();

      user.lock();

      assertThat(user.getStatus()).isEqualTo(User.Status.LOCKED);
    }
  }

  @Nested
  @DisplayName("login tracking")
  class LoginTracking {

    @Test
    @DisplayName("should record login timestamp")
    void shouldRecordLogin() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.ACTIVE)
              .build();

      assertThat(user.getLastLoginAt()).isNull();

      user.recordLogin();

      assertThat(user.getLastLoginAt()).isNotNull();
    }
  }
}
