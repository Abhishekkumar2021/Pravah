package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.AuthTokenResponse;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import io.pravah.tenant.infrastructure.config.AuthProperties;
import io.pravah.tenant.infrastructure.persistence.AuthRlsHelper;
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email/password authentication (US-10.01). */
@Service
@Transactional
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String INVALID_CREDENTIALS = "Invalid email or password";

  private final UserRepository userRepository;
  private final TenantMemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenIssuer jwtTokenIssuer;
  private final AuthProperties authProperties;
  private final AuthRlsHelper authRlsHelper;
  private final RoleService roleService;

  public AuthService(
      UserRepository userRepository,
      TenantMemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenIssuer jwtTokenIssuer,
      AuthProperties authProperties,
      AuthRlsHelper authRlsHelper,
      RoleService roleService) {
    this.userRepository = userRepository;
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenIssuer = jwtTokenIssuer;
    this.authProperties = authProperties;
    this.authRlsHelper = authRlsHelper;
    this.roleService = roleService;
  }

  /**
   * Authenticates a user by email and password.
   *
   * <p>Uses RLS bypass for the initial email lookup (user's tenant is unknown until we find them).
   * After finding the user, sets tenant context for subsequent operations like recording login
   * attempts.
   */
  @Transactional
  public AuthTokenResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());

    // Enable RLS bypass for email lookup (tenant unknown at this point)
    authRlsHelper.enableAuthLookup();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS));

    // Now we know the tenant - set context for subsequent operations
    authRlsHelper.disableAuthLookup();
    TenantContext.setCurrentTenantId(user.getTenantId());
    TenantContext.setCurrentUserId(user.getId());

    // Use generic message for all auth failures to prevent user enumeration
    if (user.isAccountLocked()) {
      log.debug("Login attempt on locked account", kv("user_id", user.getId()));
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }
    if (!user.isActive()) {
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      user.recordFailedLogin(authProperties.maxFailedAttempts(), authProperties.lockDuration());
      userRepository.save(user);
      log.info(
          "Failed login attempt",
          kv("user_id", user.getId()),
          kv("failed_attempts", user.getFailedLoginAttempts()));
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }

    user.recordLogin();
    userRepository.save(user);
    log.info("User logged in", kv("user_id", user.getId()), kv("tenant_id", user.getTenantId()));
    return issueToken(user);
  }

  public AuthTokenResponse register(RegisterRequest request) {
    UUID tenantId = authProperties.registrationTenantId();
    String email = normalizeEmail(request.email());

    // Enable RLS bypass for duplicate check (no tenant context set during registration)
    authRlsHelper.enableAuthLookup();
    boolean exists = userRepository.existsByTenantIdAndEmail(tenantId, email);
    authRlsHelper.disableAuthLookup();

    if (exists) {
      throw ValidationException.of("email", "User with this email already exists");
    }

    // Set tenant context for user creation and subsequent operations
    TenantContext.setCurrentTenantId(tenantId);

    User user =
        User.builder()
            .tenantId(tenantId)
            .email(email)
            .name(request.name().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            // Alpha: activate immediately; email verification tracked in US-10.01 follow-up.
            .status(User.Status.ACTIVE)
            .build();
    user = userRepository.save(user);

    TenantMember membership = new TenantMember(tenantId, user.getId(), Role.VIEWER_ROLE_ID);
    memberRepository.save(membership);

    TenantContext.setCurrentUserId(user.getId());

    user.recordLogin();
    userRepository.save(user);
    log.info(
        "User registered",
        kv("user_id", user.getId()),
        kv("tenant_id", tenantId),
        kv("email", email));
    return issueToken(user);
  }

  /**
   * Accepts reset requests without revealing whether the email exists (alpha stub).
   *
   * <p>Always returns success to prevent email enumeration.
   */
  @Transactional(readOnly = true)
  public void requestPasswordReset(String email) {
    String normalized = normalizeEmail(email);

    // Enable RLS bypass for email lookup
    authRlsHelper.enableAuthLookup();
    userRepository
        .findByEmail(normalized)
        .ifPresent(
            user ->
                log.info(
                    "Password reset requested (email delivery not implemented)",
                    kv("user_id", user.getId())));
  }

  private AuthTokenResponse issueToken(User user) {
    RoleService.UserAuthorization authorization =
        roleService.resolveAuthorization(user.getTenantId(), user.getId());
    String accessToken =
        jwtTokenIssuer.generateAccessToken(
            user.getId(),
            user.getTenantId(),
            List.of(authorization.role().name()),
            authorization.permissions());
    Instant expiresAt = Instant.now().plusSeconds(15 * 60);
    return new AuthTokenResponse(
        accessToken,
        user.getId(),
        user.getTenantId(),
        expiresAt,
        UserResponse.from(user, authorization.role()));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
