package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.CreateUserRequest;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private UserRepository userRepository;
  @Mock private TenantMemberRepository memberRepository;
  @Mock private RoleService roleService;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, memberRepository, roleService, passwordEncoder);
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
    lenient()
        .when(roleService.getUserRoleSummary(eq(TENANT_ID), any(UUID.class)))
        .thenReturn(new UserRoleSummary(Role.VIEWER_ROLE_ID, "viewer"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createUser_persistsUserAndMembership() {
    when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "new@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        userService.createUser(
            new CreateUserRequest(
                "new@example.com", "New User", "Password1!", Role.EDITOR_ROLE_ID));

    assertThat(response.email()).isEqualTo("new@example.com");
    verify(memberRepository).save(any(TenantMember.class));
  }

  @Test
  void createUser_rejectsDuplicateEmail() {
    when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "dup@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                userService.createUser(
                    new CreateUserRequest("dup@example.com", "Dup", "Password1!", null)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void getUser_returnsResponse() {
    User user = activeUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertThat(userService.getUser(USER_ID).id()).isEqualTo(USER_ID);
  }

  @Test
  void getUser_throwsWhenMissing() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getUser(USER_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void listUsers_mapsAllUsersInTenant() {
    when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(activeUser()));

    assertThat(userService.listUsers()).hasSize(1);
  }

  @Test
  void updateUserName_persistsChange() {
    TenantContext.setCurrentUserId(USER_ID);
    User user = activeUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    assertThat(userService.updateUserName(USER_ID, "Renamed").name()).isEqualTo("Renamed");
  }

  @Test
  void updatePassword_requiresCurrentUser() {
    TenantContext.setCurrentUserId(UUID.randomUUID());

    assertThatThrownBy(() -> userService.updatePassword(USER_ID, "old", "Newpass1!"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void updatePassword_rejectsWrongCurrentPassword() {
    User user = activeUser();
    user.updatePasswordHash(passwordEncoder.encode("Correct1!"));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.updatePassword(USER_ID, "wrong", "Newpass1!"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void updatePassword_succeedsForMatchingUser() {
    User user = activeUser();
    String hash = passwordEncoder.encode("Correct1!");
    user.updatePasswordHash(hash);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userService.updatePassword(USER_ID, "Correct1!", "Newpass1!");

    verify(userRepository).save(user);
    assertThat(passwordEncoder.matches("Newpass1!", user.getPasswordHash())).isTrue();
  }

  @Test
  void assignRole_delegatesToRoleService() {
    User user = activeUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userService.assignRole(USER_ID, Role.ADMIN_ROLE_ID);

    verify(roleService).assignRole(USER_ID, Role.ADMIN_ROLE_ID);
  }

  @Test
  void getUserByEmail_returnsUser() {
    User user = activeUser();
    when(userRepository.findByTenantIdAndEmail(TENANT_ID, "dev@localhost.pravah"))
        .thenReturn(Optional.of(user));

    assertThat(userService.getUserByEmail("dev@localhost.pravah").email())
        .isEqualTo("dev@localhost.pravah");
  }

  @Test
  void activateUser_activatesPendingUser() {
    User user =
        User.builder()
            .id(USER_ID)
            .tenantId(TENANT_ID)
            .email("dev@localhost.pravah")
            .name("Dev")
            .status(User.Status.PENDING)
            .build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    assertThat(userService.activateUser(USER_ID).status()).isEqualTo(User.Status.ACTIVE);
  }

  @Test
  void deactivateUser_setsInactive() {
    User user = activeUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    assertThat(userService.deactivateUser(USER_ID).status()).isEqualTo(User.Status.INACTIVE);
  }

  @Test
  void lockUser_setsLocked() {
    User user = activeUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    assertThat(userService.lockUser(USER_ID).status()).isEqualTo(User.Status.LOCKED);
  }

  @Test
  void createUser_requiresTenantContext() {
    TenantContext.clear();

    assertThatThrownBy(
            () -> userService.createUser(new CreateUserRequest("x@example.com", "X", null, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static User activeUser() {
    return User.builder()
        .id(USER_ID)
        .tenantId(TENANT_ID)
        .email("dev@localhost.pravah")
        .name("Dev User")
        .status(User.Status.ACTIVE)
        .build();
  }
}
