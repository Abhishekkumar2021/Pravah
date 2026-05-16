package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.ValidationException;
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
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.time.Instant;
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

  public AuthService(
      UserRepository userRepository,
      TenantMemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenIssuer jwtTokenIssuer,
      AuthProperties authProperties) {
    this.userRepository = userRepository;
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenIssuer = jwtTokenIssuer;
    this.authProperties = authProperties;
  }

  @Transactional(readOnly = true)
  public AuthTokenResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS));

    if (user.isAccountLocked()) {
      throw new AuthenticationException("Account is temporarily locked. Try again later.");
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

    if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
      throw ValidationException.of("email", "User with this email already exists");
    }

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

    user.recordLogin();
    userRepository.save(user);
    log.info(
        "User registered",
        kv("user_id", user.getId()),
        kv("tenant_id", tenantId),
        kv("email", email));
    return issueToken(user);
  }

  /** Accepts reset requests without revealing whether the email exists (alpha stub). */
  @Transactional(readOnly = true)
  public void requestPasswordReset(String email) {
    String normalized = normalizeEmail(email);
    userRepository
        .findByEmail(normalized)
        .ifPresent(
            user ->
                log.info(
                    "Password reset requested (email delivery not implemented)",
                    kv("user_id", user.getId())));
  }

  private AuthTokenResponse issueToken(User user) {
    String accessToken = jwtTokenIssuer.generateAccessToken(user.getId(), user.getTenantId());
    Instant expiresAt = Instant.now().plusSeconds(15 * 60);
    return new AuthTokenResponse(
        accessToken, user.getId(), user.getTenantId(), expiresAt, UserResponse.from(user));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
