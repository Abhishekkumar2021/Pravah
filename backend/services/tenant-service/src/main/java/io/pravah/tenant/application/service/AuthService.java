package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.EmailNotVerifiedException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.tenant.application.dto.AuthTokenResponse;
import io.pravah.tenant.application.dto.ConfirmPasswordResetRequest;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.application.dto.RegisterResponse;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.domain.model.EmailVerificationToken;
import io.pravah.tenant.domain.model.PasswordResetToken;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.EmailVerificationTokenRepository;
import io.pravah.tenant.domain.repository.PasswordResetTokenRepository;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import io.pravah.tenant.infrastructure.config.AuthProperties;
import io.pravah.tenant.infrastructure.email.EmailVerificationEmailService;
import io.pravah.tenant.infrastructure.email.PasswordResetEmailService;
import io.pravah.tenant.infrastructure.persistence.AuthRlsHelper;
import io.pravah.tenant.infrastructure.security.ApiTokenHasher;
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import io.pravah.tenant.infrastructure.security.PasswordResetTokenGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email/password authentication (US-10.01). */
@Service
@Transactional
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String INVALID_CREDENTIALS = "Invalid email or password";
  private static final String REGISTRATION_MESSAGE =
      "Check your email for a verification link before signing in.";

  private final UserRepository userRepository;
  private final TenantMemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenIssuer jwtTokenIssuer;
  private final AuthProperties authProperties;
  private final AuthRlsHelper authRlsHelper;
  private final RoleService roleService;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordResetEmailService passwordResetEmailService;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final EmailVerificationEmailService emailVerificationEmailService;
  private final JwtTokenVerifier jwtTokenVerifier;
  private final java.util.Optional<io.pravah.tenant.infrastructure.security.JwtRevocationService>
      jwtRevocationService;

  public AuthService(
      UserRepository userRepository,
      TenantMemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenIssuer jwtTokenIssuer,
      AuthProperties authProperties,
      AuthRlsHelper authRlsHelper,
      RoleService roleService,
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordResetEmailService passwordResetEmailService,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      EmailVerificationEmailService emailVerificationEmailService,
      JwtTokenVerifier jwtTokenVerifier,
      java.util.Optional<io.pravah.tenant.infrastructure.security.JwtRevocationService>
          jwtRevocationService) {
    this.userRepository = userRepository;
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenIssuer = jwtTokenIssuer;
    this.authProperties = authProperties;
    this.authRlsHelper = authRlsHelper;
    this.roleService = roleService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordResetEmailService = passwordResetEmailService;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.emailVerificationEmailService = emailVerificationEmailService;
    this.jwtTokenVerifier = jwtTokenVerifier;
    this.jwtRevocationService = jwtRevocationService;
  }

  /** Returns the authenticated user (US-10.01 / CLI). */
  @Transactional(readOnly = true)
  public UserResponse currentUser() {
    UUID userId = TenantContext.getCurrentUserId();
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (userId == null || tenantId == null) {
      throw new AuthenticationException("Not authenticated");
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new AuthenticationException("Not authenticated"));
    if (!user.getTenantId().equals(tenantId)) {
      throw new AuthenticationException("Not authenticated");
    }
    RoleService.UserAuthorization authorization =
        roleService.resolveAuthorization(tenantId, userId);
    return UserResponse.from(user, authorization.role());
  }

  /** Revokes the current access token (Redis blocklist when configured). */
  public void logout(String accessToken) {
    JwtClaims claims = jwtTokenVerifier.validateAndGetClaims(accessToken);
    if (claims.jwtId() != null && claims.expiresAt() != null) {
      jwtRevocationService.ifPresent(s -> s.revoke(claims.jwtId(), claims.expiresAt()));
    }
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

    authRlsHelper.enableAuthLookup();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS));

    authRlsHelper.disableAuthLookup();
    TenantContext.setCurrentTenantId(user.getTenantId());
    TenantContext.setCurrentUserId(user.getId());

    if (user.isAccountLocked()) {
      log.debug("Login attempt on locked account", kv("user_id", user.getId()));
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }

    boolean passwordMatches =
        user.getPasswordHash() != null
            && passwordEncoder.matches(request.password(), user.getPasswordHash());

    if (user.getStatus() == User.Status.PENDING) {
      if (passwordMatches) {
        throw new EmailNotVerifiedException();
      }
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }

    if (!user.isActive()) {
      throw new AuthenticationException(INVALID_CREDENTIALS);
    }

    if (!passwordMatches) {
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

  /**
   * Self-service registration (US-10.01). Creates a pending user and sends a verification email.
   *
   * <p>Does not issue a JWT until {@link #verifyEmail(String)} succeeds.
   */
  public RegisterResponse register(RegisterRequest request) {
    UUID tenantId = authProperties.registrationTenantId();
    String email = normalizeEmail(request.email());

    authRlsHelper.enableAuthLookup();
    var existing = userRepository.findByEmail(email);
    if (existing.isPresent()) {
      User user = existing.get();
      authRlsHelper.disableAuthLookup();
      if (user.getStatus() == User.Status.PENDING) {
        TenantContext.setCurrentTenantId(user.getTenantId());
        user.updateName(request.name().trim());
        user.updatePasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        queueVerificationEmail(user);
        log.info(
            "Re-registration updated pending user",
            kv("user_id", user.getId()),
            kv("email", email));
        return new RegisterResponse(email, REGISTRATION_MESSAGE);
      }
      throw ValidationException.of("email", "User with this email already exists");
    }
    authRlsHelper.disableAuthLookup();

    TenantContext.setCurrentTenantId(tenantId);

    User user =
        User.builder()
            .tenantId(tenantId)
            .email(email)
            .name(request.name().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .status(User.Status.PENDING)
            .build();
    user = userRepository.save(user);

    TenantMember membership = new TenantMember(tenantId, user.getId(), Role.VIEWER_ROLE_ID);
    memberRepository.save(membership);

    queueVerificationEmail(user);
    log.info(
        "User registered (pending verification)",
        kv("user_id", user.getId()),
        kv("tenant_id", tenantId),
        kv("email", email));
    return new RegisterResponse(email, REGISTRATION_MESSAGE);
  }

  /** Activates a pending account using the verification token from email (US-10.01). */
  @Transactional
  public void verifyEmail(String rawToken) {
    String tokenHash = ApiTokenHasher.hash(rawToken.trim());
    Instant now = Instant.now();

    authRlsHelper.enableAuthLookup();
    EmailVerificationToken verificationToken =
        emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () -> ValidationException.of("token", "Invalid or expired verification link"));

    User user =
        userRepository
            .findById(verificationToken.getUserId())
            .orElseThrow(
                () -> ValidationException.of("token", "Invalid or expired verification link"));

    TenantContext.setCurrentTenantId(user.getTenantId());
    authRlsHelper.disableAuthLookup();

    if (user.isActive()) {
      log.debug("Email verify skipped: account already active", kv("user_id", user.getId()));
      return;
    }

    if (verificationToken.isUsed() || verificationToken.isExpired(now)) {
      throw ValidationException.of("token", "Invalid or expired verification link");
    }

    if (user.getStatus() == User.Status.PENDING) {
      try {
        user.activate();
        userRepository.save(user);
      } catch (OptimisticLockingFailureException ex) {
        User latest = userRepository.findById(user.getId()).orElseThrow(() -> ex);
        if (!latest.isActive()) {
          throw ex;
        }
        log.debug("Email verify concurrent activation resolved", kv("user_id", latest.getId()));
      }
    }

    verificationToken.markUsed(now);
    emailVerificationTokenRepository.save(verificationToken);
    log.info("Email verified", kv("user_id", user.getId()));
  }

  /**
   * Re-sends verification email for a pending account (US-10.01).
   *
   * <p>Always completes without error to prevent email enumeration.
   */
  @Transactional
  public void resendVerificationEmail(String email) {
    String normalized = normalizeEmail(email);

    authRlsHelper.enableAuthLookup();
    userRepository
        .findByEmail(normalized)
        .filter(user -> user.getStatus() == User.Status.PENDING)
        .ifPresent(
            user -> {
              TenantContext.setCurrentTenantId(user.getTenantId());
              queueVerificationEmail(user);
            });
    authRlsHelper.disableAuthLookup();
  }

  /**
   * Issues a single-use reset token and emails a link (US-10.01).
   *
   * <p>Always completes without error to prevent email enumeration.
   */
  @Transactional
  public void requestPasswordReset(String email) {
    String normalized = normalizeEmail(email);
    Instant now = Instant.now();

    authRlsHelper.enableAuthLookup();
    userRepository
        .findByEmail(normalized)
        .ifPresent(
            user -> {
              if (!user.isActive()) {
                log.debug(
                    "Password reset skipped for non-active user", kv("user_id", user.getId()));
                return;
              }
              String rawToken = PasswordResetTokenGenerator.generate();
              String tokenHash = ApiTokenHasher.hash(rawToken);
              Instant expiresAt = now.plus(authProperties.passwordResetTokenTtl());
              passwordResetTokenRepository.save(
                  PasswordResetToken.create(user.getId(), tokenHash, expiresAt));
              try {
                passwordResetEmailService.sendResetLink(user.getEmail(), rawToken);
                log.info("Password reset email queued", kv("user_id", user.getId()));
              } catch (RuntimeException e) {
                log.error(
                    "Password reset email failed",
                    kv("user_id", user.getId()),
                    kv("error", e.getMessage()));
              }
            });
    authRlsHelper.disableAuthLookup();
  }

  /** Applies a new password using a valid reset token (US-10.01). */
  @Transactional
  public void confirmPasswordReset(ConfirmPasswordResetRequest request) {
    String tokenHash = ApiTokenHasher.hash(request.token().trim());
    Instant now = Instant.now();

    authRlsHelper.enableAuthLookup();
    PasswordResetToken resetToken =
        passwordResetTokenRepository
            .findByTokenHashAndUsedAtIsNull(tokenHash)
            .orElseThrow(() -> ValidationException.of("token", "Invalid or expired reset link"));

    User user =
        userRepository
            .findById(resetToken.getUserId())
            .orElseThrow(() -> ValidationException.of("token", "Invalid or expired reset link"));

    TenantContext.setCurrentTenantId(user.getTenantId());
    authRlsHelper.disableAuthLookup();

    if (resetToken.isUsed() || resetToken.isExpired(now)) {
      throw ValidationException.of("token", "Invalid or expired reset link");
    }
    user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    resetToken.markUsed(now);
    passwordResetTokenRepository.save(resetToken);
    log.info("Password reset completed", kv("user_id", user.getId()));
  }

  private void queueVerificationEmail(User user) {
    emailVerificationTokenRepository.deleteUnusedByUserId(user.getId());
    String rawToken = PasswordResetTokenGenerator.generate();
    String tokenHash = ApiTokenHasher.hash(rawToken);
    Instant expiresAt = Instant.now().plus(authProperties.emailVerificationTokenTtl());
    emailVerificationTokenRepository.save(
        EmailVerificationToken.create(user.getId(), tokenHash, expiresAt));
    try {
      emailVerificationEmailService.sendVerificationLink(user.getEmail(), rawToken);
      log.info("Verification email queued", kv("user_id", user.getId()));
    } catch (RuntimeException e) {
      log.error(
          "Verification email failed", kv("user_id", user.getId()), kv("error", e.getMessage()));
      throw e;
    }
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
