package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.CreateUserRequest;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for user management.
 *
 * <p>Handles user lifecycle within the current tenant context.
 */
@Service
@Transactional
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;
  private final TenantMemberRepository memberRepository;
  private final RoleService roleService;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      TenantMemberRepository memberRepository,
      RoleService roleService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.memberRepository = memberRepository;
    this.roleService = roleService;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Creates a new user in the current tenant.
   *
   * @param request the creation request
   * @return the created user
   * @throws ValidationException if email already exists in the tenant
   */
  public UserResponse createUser(CreateUserRequest request) {
    UUID tenantId = requireTenantContext();

    if (userRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
      throw ValidationException.of("email", "User with this email already exists in tenant");
    }

    User user =
        User.builder()
            .tenantId(tenantId)
            .email(request.email())
            .name(request.name())
            .passwordHash(
                request.password() != null ? passwordEncoder.encode(request.password()) : null)
            .status(User.Status.PENDING)
            .build();

    user = userRepository.save(user);
    log.debug("Created user", kv("user_id", user.getId()), kv("email", user.getEmail()));

    UUID roleId = request.roleId() != null ? request.roleId() : Role.VIEWER_ROLE_ID;
    TenantMember membership = new TenantMember(tenantId, user.getId(), roleId);
    memberRepository.save(membership);
    log.debug("Assigned role to user", kv("role_id", roleId), kv("user_id", user.getId()));

    log.info(
        "User created",
        kv("user_id", user.getId()),
        kv("email", user.getEmail()),
        kv("tenant_id", tenantId),
        kv("role_id", roleId));
    return toUserResponse(user);
  }

  /**
   * Assigns a built-in (or tenant custom) role to a user.
   *
   * @param userId the user to update
   * @param roleId the new role ID
   */
  public UserResponse assignRole(UUID userId, UUID roleId) {
    roleService.assignRole(userId, roleId);
    return getUser(userId);
  }

  /**
   * Gets a user by ID.
   *
   * @param userId the user ID
   * @return the user
   * @throws EntityNotFoundException if not found
   */
  @Transactional(readOnly = true)
  public UserResponse getUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));
    return toUserResponse(user);
  }

  /**
   * Gets a user by email in the current tenant.
   *
   * @param email the email address
   * @return the user
   * @throws EntityNotFoundException if not found
   */
  @Transactional(readOnly = true)
  public UserResponse getUserByEmail(String email) {
    UUID tenantId = requireTenantContext();
    User user =
        userRepository
            .findByTenantIdAndEmail(tenantId, email)
            .orElseThrow(() -> new EntityNotFoundException("User with email: " + email));
    return toUserResponse(user);
  }

  /**
   * Lists all users in the current tenant.
   *
   * @return list of users
   */
  @Transactional(readOnly = true)
  public List<UserResponse> listUsers() {
    UUID tenantId = requireTenantContext();
    return userRepository.findByTenantId(tenantId).stream().map(this::toUserResponse).toList();
  }

  /**
   * Updates a user's name.
   *
   * @param userId the user ID
   * @param newName the new name
   * @return the updated user
   */
  public UserResponse updateUserName(UUID userId, String newName) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    String oldName = user.getName();
    user.updateName(newName);
    user = userRepository.save(user);

    log.info(
        "Updated user name",
        kv("user_id", userId),
        kv("old_name", oldName),
        kv("new_name", newName));
    return toUserResponse(user);
  }

  /**
   * Activates a pending user.
   *
   * @param userId the user ID
   * @return the updated user
   */
  public UserResponse activateUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    user.activate();
    user = userRepository.save(user);

    log.info("Activated user", kv("user_id", userId));
    return toUserResponse(user);
  }

  /**
   * Deactivates a user.
   *
   * @param userId the user ID
   * @return the updated user
   */
  public UserResponse deactivateUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    user.deactivate();
    user = userRepository.save(user);

    log.info("Deactivated user", kv("user_id", userId));
    return toUserResponse(user);
  }

  /**
   * Locks a user account (e.g., for security reasons).
   *
   * @param userId the user ID
   * @return the updated user
   */
  public UserResponse lockUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    user.lock();
    user = userRepository.save(user);

    log.info("Locked user", kv("user_id", userId), kv("reason", "manual_lock"));
    return toUserResponse(user);
  }

  /**
   * Updates a user's password after verifying the current password.
   *
   * @param userId the user ID
   * @param currentPassword the current password for verification
   * @param newPassword the new password
   * @throws ValidationException if current password is incorrect
   */
  public void updatePassword(UUID userId, String currentPassword, String newPassword) {
    UUID currentUserId = TenantContext.getCurrentUserId();
    if (currentUserId == null || !currentUserId.equals(userId)) {
      throw ValidationException.of("userId", "You can only change your own password");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    if (user.getPasswordHash() != null
        && !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw ValidationException.of("currentPassword", "Current password is incorrect");
    }

    user.updatePasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    log.info("Updated password for user", kv("user_id", userId));
  }

  /**
   * Records a user login.
   *
   * @param userId the user ID
   */
  public void recordLogin(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

    user.recordLogin();
    userRepository.save(user);

    log.debug("Recorded login", kv("user_id", userId));
  }

  private UserResponse toUserResponse(User user) {
    UUID tenantId = requireTenantContext();
    return UserResponse.from(user, roleService.getUserRoleSummary(tenantId, user.getId()));
  }

  private UUID requireTenantContext() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException(
          "No tenant context set. Ensure request is authenticated and TenantContext is populated from JWT.");
    }
    return tenantId;
  }
}
