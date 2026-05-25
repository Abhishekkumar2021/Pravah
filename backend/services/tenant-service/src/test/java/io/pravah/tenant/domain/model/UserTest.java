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
  @DisplayName("MFA")
  class Mfa {

    @Test
    @DisplayName("should enable and disable MFA")
    void shouldToggleMfa() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.ACTIVE)
              .build();

      assertThat(user.isMfaEnabled()).isFalse();
      user.enableMfa("secret");
      assertThat(user.isMfaEnabled()).isTrue();
      user.disableMfa();
      assertThat(user.isMfaEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("account lockout")
  class AccountLockout {

    @Test
    @DisplayName("should lock account after max failed attempts")
    void shouldLockAfterFailedAttempts() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.ACTIVE)
              .build();

      user.recordFailedLogin(3, java.time.Duration.ofMinutes(15));
      user.recordFailedLogin(3, java.time.Duration.ofMinutes(15));
      user.recordFailedLogin(3, java.time.Duration.ofMinutes(15));

      assertThat(user.getStatus()).isEqualTo(User.Status.LOCKED);
      assertThat(user.isAccountLocked()).isTrue();
    }

    @Test
    @DisplayName("should unlock account and reset counters")
    void shouldUnlockAccount() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.LOCKED)
              .build();
      user.recordFailedLogin(1, java.time.Duration.ofMinutes(5));
      user.unlockAccount();

      assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
      assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("should not activate locked user")
    void shouldNotActivateLockedUser() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("test@example.com")
              .name("Test")
              .status(User.Status.LOCKED)
              .build();

      assertThatThrownBy(user::activate).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("profile updates")
  class ProfileUpdates {

    @Test
    @DisplayName("should update name and email")
    void shouldUpdateProfileFields() {
      User user =
          User.builder()
              .tenantId(TENANT_ID)
              .email("old@example.com")
              .name("Old")
              .status(User.Status.ACTIVE)
              .build();

      user.updateName("New");
      user.updateEmail("new@example.com");

      assertThat(user.getName()).isEqualTo("New");
      assertThat(user.getEmail()).isEqualTo("new@example.com");
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
