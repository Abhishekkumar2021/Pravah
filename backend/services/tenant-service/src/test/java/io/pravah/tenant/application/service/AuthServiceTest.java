package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.EmailNotVerifiedException;
import io.pravah.common.exception.ValidationException;
import io.pravah.tenant.application.dto.ConfirmPasswordResetRequest;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.application.dto.UserRoleSummary;
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
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private UserRepository userRepository;
  @Mock private TenantMemberRepository memberRepository;
  @Mock private JwtTokenIssuer jwtTokenIssuer;
  @Mock private AuthRlsHelper authRlsHelper;
  @Mock private RoleService roleService;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private PasswordResetEmailService passwordResetEmailService;
  @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Mock private EmailVerificationEmailService emailVerificationEmailService;
  @Mock private io.pravah.spring.security.JwtTokenVerifier jwtTokenVerifier;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
  private AuthService authService;
  private String passwordHash;

  @BeforeEach
  void setUp() {
    AuthProperties properties =
        new AuthProperties(
            5,
            Duration.ofMinutes(15),
            TENANT_ID,
            Duration.ofHours(1),
            Duration.ofHours(24),
            "http://localhost:5173",
            "noreply@localhost.pravah");
    authService =
        new AuthService(
            userRepository,
            memberRepository,
            passwordEncoder,
            jwtTokenIssuer,
            properties,
            authRlsHelper,
            roleService,
            passwordResetTokenRepository,
            passwordResetEmailService,
            emailVerificationTokenRepository,
            emailVerificationEmailService,
            jwtTokenVerifier,
            Optional.empty());
    passwordHash = passwordEncoder.encode("PravahDev1!");
    lenient()
        .when(roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .thenReturn(
            new RoleService.UserAuthorization(
                new UserRoleSummary(Role.OWNER_ROLE_ID, "owner"), List.of("*")));
  }

  @Test
  void login_validCredentials_returnsToken() {
    User user = activeUser();
    when(userRepository.findByEmail("dev@localhost.pravah")).thenReturn(Optional.of(user));
    when(jwtTokenIssuer.generateAccessToken(USER_ID, TENANT_ID, List.of("owner"), List.of("*")))
        .thenReturn("jwt-token");

    var response = authService.login(new LoginRequest("dev@localhost.pravah", "PravahDev1!"));

    assertThat(response.accessToken()).isEqualTo("jwt-token");
    assertThat(response.user().email()).isEqualTo("dev@localhost.pravah");
    verify(userRepository).save(user);
    assertThat(user.getFailedLoginAttempts()).isZero();
  }

  @Test
  void login_pendingUserWithCorrectPassword_throwsEmailNotVerified() {
    User user = pendingUser();
    when(userRepository.findByEmail("new@localhost.pravah")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("new@localhost.pravah", "PravahDev1!")))
        .isInstanceOf(EmailNotVerifiedException.class);

    verify(jwtTokenIssuer, never()).generateAccessToken(any(), any(), any(), any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void login_wrongPassword_incrementsFailures() {
    User user = activeUser();
    when(userRepository.findByEmail("dev@localhost.pravah")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("dev@localhost.pravah", "wrong-password")))
        .isInstanceOf(AuthenticationException.class);

    verify(userRepository).save(user);
    assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    verify(jwtTokenIssuer, never()).generateAccessToken(any(), any(), any(), any());
  }

  @Test
  void login_unknownEmail_throwsWithoutSave() {
    when(userRepository.findByEmail("missing@localhost.pravah")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("missing@localhost.pravah", "PravahDev1!")))
        .isInstanceOf(AuthenticationException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void register_createsPendingUserAndSendsVerificationEmail() {
    when(userRepository.findByEmail("new@localhost.pravah")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              return User.builder()
                  .id(USER_ID)
                  .tenantId(u.getTenantId())
                  .email(u.getEmail())
                  .name(u.getName())
                  .passwordHash(u.getPasswordHash())
                  .status(u.getStatus())
                  .build();
            });

    var response =
        authService.register(
            new RegisterRequest("new@localhost.pravah", "PravahDev1!", "New User"));

    assertThat(response.email()).isEqualTo("new@localhost.pravah");
    assertThat(response.message()).contains("verification");
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(User.Status.PENDING);
    verify(memberRepository).save(any(TenantMember.class));
    verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    verify(emailVerificationEmailService)
        .sendVerificationLink(eq("new@localhost.pravah"), any(String.class));
    verify(jwtTokenIssuer, never()).generateAccessToken(any(), any(), any(), any());
  }

  @Test
  void register_duplicateActiveEmail_throwsValidation() {
    when(userRepository.findByEmail("dev@localhost.pravah")).thenReturn(Optional.of(activeUser()));

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("dev@localhost.pravah", "PravahDev1!", "Dev")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void register_duplicatePendingEmail_updatesCredentialsAndResendsVerification() {
    User existingUser = pendingUser();
    when(userRepository.findByEmail("new@localhost.pravah")).thenReturn(Optional.of(existingUser));

    var response =
        authService.register(
            new RegisterRequest("new@localhost.pravah", "NewPassword1!", "Updated Name"));

    assertThat(response.email()).isEqualTo("new@localhost.pravah");
    assertThat(existingUser.getName()).isEqualTo("Updated Name");
    assertThat(passwordEncoder.matches("NewPassword1!", existingUser.getPasswordHash())).isTrue();
    verify(userRepository).save(existingUser);
    verify(emailVerificationEmailService)
        .sendVerificationLink(eq("new@localhost.pravah"), any(String.class));
  }

  @Test
  void verifyEmail_validToken_activatesUser() {
    User user = pendingUser();
    String rawToken = "verify-token-raw";
    String tokenHash = io.pravah.tenant.infrastructure.security.ApiTokenHasher.hash(rawToken);
    Instant now = Instant.now();
    EmailVerificationToken verificationToken =
        EmailVerificationToken.create(user.getId(), tokenHash, now.plus(Duration.ofHours(24)));

    when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(verificationToken));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    authService.verifyEmail(rawToken);

    assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
    verify(userRepository).save(user);
    verify(emailVerificationTokenRepository).save(verificationToken);
    assertThat(verificationToken.isUsed()).isTrue();
  }

  @Test
  void verifyEmail_alreadyActive_isIdempotent() {
    User user = activeUser();
    String rawToken = "verify-token-raw";
    String tokenHash = io.pravah.tenant.infrastructure.security.ApiTokenHasher.hash(rawToken);
    EmailVerificationToken verificationToken =
        EmailVerificationToken.create(
            user.getId(), tokenHash, Instant.now().plus(Duration.ofHours(24)));

    when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(verificationToken));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    authService.verifyEmail(rawToken);

    verify(userRepository, never()).save(any());
    verify(emailVerificationTokenRepository, never()).save(any());
  }

  @Test
  void resendVerification_pendingUser_sendsEmail() {
    when(userRepository.findByEmail("new@localhost.pravah")).thenReturn(Optional.of(pendingUser()));

    authService.resendVerificationEmail("new@localhost.pravah");

    verify(emailVerificationEmailService)
        .sendVerificationLink(eq("new@localhost.pravah"), any(String.class));
  }

  @Test
  void requestPasswordReset_activeUser_savesTokenAndSendsEmail() {
    User user = activeUser();
    when(userRepository.findByEmail("dev@localhost.pravah")).thenReturn(Optional.of(user));

    authService.requestPasswordReset("dev@localhost.pravah");

    verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    verify(passwordResetEmailService).sendResetLink(eq("dev@localhost.pravah"), any(String.class));
  }

  @Test
  void requestPasswordReset_unknownEmail_doesNotSendEmail() {
    when(userRepository.findByEmail("missing@localhost.pravah")).thenReturn(Optional.empty());

    authService.requestPasswordReset("missing@localhost.pravah");

    verify(passwordResetTokenRepository, never()).save(any());
    verify(passwordResetEmailService, never()).sendResetLink(any(), any());
  }

  @Test
  void confirmPasswordReset_validToken_updatesPasswordAndMarksTokenUsed() {
    User user = activeUser();
    String rawToken = "reset-token-raw";
    String tokenHash = io.pravah.tenant.infrastructure.security.ApiTokenHasher.hash(rawToken);
    Instant now = Instant.now();
    PasswordResetToken resetToken =
        PasswordResetToken.create(user.getId(), tokenHash, now.plus(Duration.ofHours(1)));

    when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash))
        .thenReturn(Optional.of(resetToken));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    authService.confirmPasswordReset(new ConfirmPasswordResetRequest(rawToken, "NewPassword1!"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(passwordEncoder.matches("NewPassword1!", userCaptor.getValue().getPasswordHash()))
        .isTrue();
    verify(passwordResetTokenRepository).save(resetToken);
    assertThat(resetToken.isUsed()).isTrue();
  }

  private User activeUser() {
    return User.builder()
        .id(USER_ID)
        .tenantId(TENANT_ID)
        .email("dev@localhost.pravah")
        .name("Dev User")
        .passwordHash(passwordHash)
        .status(User.Status.ACTIVE)
        .build();
  }

  private User pendingUser() {
    return User.builder()
        .id(USER_ID)
        .tenantId(TENANT_ID)
        .email("new@localhost.pravah")
        .name("New User")
        .passwordHash(passwordHash)
        .status(User.Status.PENDING)
        .build();
  }
}
