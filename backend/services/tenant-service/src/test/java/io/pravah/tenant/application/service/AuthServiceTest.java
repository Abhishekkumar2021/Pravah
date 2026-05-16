package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.ValidationException;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import io.pravah.tenant.infrastructure.config.AuthProperties;
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.time.Duration;
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

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
  private AuthService authService;
  private String passwordHash;

  @BeforeEach
  void setUp() {
    AuthProperties properties = new AuthProperties(5, Duration.ofMinutes(15), TENANT_ID);
    authService =
        new AuthService(
            userRepository, memberRepository, passwordEncoder, jwtTokenIssuer, properties);
    passwordHash = passwordEncoder.encode("PravahDev1!");
  }

  @Test
  void login_validCredentials_returnsToken() {
    User user = activeUser();
    when(userRepository.findByEmail("dev@localhost.pravah")).thenReturn(Optional.of(user));
    when(jwtTokenIssuer.generateAccessToken(USER_ID, TENANT_ID)).thenReturn("jwt-token");

    var response = authService.login(new LoginRequest("dev@localhost.pravah", "PravahDev1!"));

    assertThat(response.accessToken()).isEqualTo("jwt-token");
    assertThat(response.user().email()).isEqualTo("dev@localhost.pravah");
    verify(userRepository).save(user);
    assertThat(user.getFailedLoginAttempts()).isZero();
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
    verify(jwtTokenIssuer, never()).generateAccessToken(any(), any());
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
  void register_createsActiveUserAndMembership() {
    when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "new@localhost.pravah"))
        .thenReturn(false);
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
    when(jwtTokenIssuer.generateAccessToken(USER_ID, TENANT_ID)).thenReturn("jwt-token");

    var response =
        authService.register(
            new RegisterRequest("new@localhost.pravah", "PravahDev1!", "New User"));

    assertThat(response.accessToken()).isEqualTo("jwt-token");
    ArgumentCaptor<TenantMember> memberCaptor = ArgumentCaptor.forClass(TenantMember.class);
    verify(memberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getUserId()).isEqualTo(USER_ID);
  }

  @Test
  void register_duplicateEmail_throwsValidation() {
    when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "dev@localhost.pravah"))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("dev@localhost.pravah", "PravahDev1!", "Dev")))
        .isInstanceOf(ValidationException.class);
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
}
