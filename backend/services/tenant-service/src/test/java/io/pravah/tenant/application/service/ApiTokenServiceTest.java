package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.tenant.application.dto.CreateApiTokenRequest;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.application.service.RoleService.UserAuthorization;
import io.pravah.tenant.domain.model.ApiToken;
import io.pravah.tenant.domain.repository.ApiTokenRepository;
import io.pravah.tenant.infrastructure.security.ApiTokenGenerator;
import io.pravah.tenant.infrastructure.security.ApiTokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID TOKEN_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock private ApiTokenRepository apiTokenRepository;
  @Mock private RoleService roleService;

  private ApiTokenService apiTokenService;

  @BeforeEach
  void setUp() {
    apiTokenService = new ApiTokenService(apiTokenRepository, roleService);
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createToken_persistsHashAndReturnsSecret() {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    when(roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .thenReturn(new UserAuthorization(new UserRoleSummary(ROLE_ID, "owner"), List.of("*")));
    when(apiTokenRepository.save(any(ApiToken.class)))
        .thenAnswer(
            inv -> {
              ApiToken token = inv.getArgument(0);
              return ApiToken.builder()
                  .id(TOKEN_ID)
                  .tenantId(token.getTenantId())
                  .userId(token.getUserId())
                  .name(token.getName())
                  .tokenHash(token.getTokenHash())
                  .permissions(token.getPermissions())
                  .expiresAt(token.getExpiresAt())
                  .build();
            });

    var response =
        apiTokenService.createToken(
            new CreateApiTokenRequest("CI token", List.of("pipelines:read"), expiresAt));

    assertThat(response.secret()).startsWith(ApiTokenGenerator.PREFIX);
    ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
    verify(apiTokenRepository).save(captor.capture());
    assertThat(captor.getValue().getTokenHash()).isEqualTo(ApiTokenHasher.hash(response.secret()));
    assertThat(response.token().name()).isEqualTo("CI token");
    assertThat(response.token().permissions()).containsExactly("pipelines:read");
  }

  @Test
  void createToken_rejectsUnauthorizedPermissions() {
    when(roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .thenReturn(
            new UserAuthorization(
                new UserRoleSummary(ROLE_ID, "editor"), List.of("pipelines:read")));

    assertThatThrownBy(
            () ->
                apiTokenService.createToken(
                    new CreateApiTokenRequest(
                        "Bad token", List.of("users:*"), Instant.now().plusSeconds(3600))))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            ex -> {
              ValidationException ve = (ValidationException) ex;
              assertThat(ve.getFieldErrors()).hasSize(1);
              assertThat(ve.getFieldErrors().get(0).field()).isEqualTo("permissions");
              assertThat(ve.getFieldErrors().get(0).message())
                  .contains("Cannot grant permissions you do not have");
            });
  }

  @Test
  void createToken_allowsSubsetOfUserPermissions() {
    when(roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .thenReturn(
            new UserAuthorization(
                new UserRoleSummary(ROLE_ID, "admin"), List.of("pipelines:*", "executions:*")));
    when(apiTokenRepository.save(any(ApiToken.class)))
        .thenAnswer(
            inv -> {
              ApiToken token = inv.getArgument(0);
              return ApiToken.builder()
                  .id(TOKEN_ID)
                  .tenantId(token.getTenantId())
                  .userId(token.getUserId())
                  .name(token.getName())
                  .tokenHash(token.getTokenHash())
                  .permissions(token.getPermissions())
                  .expiresAt(token.getExpiresAt())
                  .build();
            });

    var response =
        apiTokenService.createToken(
            new CreateApiTokenRequest(
                "Read only", List.of("pipelines:read"), Instant.now().plusSeconds(3600)));

    assertThat(response.token().permissions()).containsExactly("pipelines:read");
  }

  @Test
  void revokeToken_setsRevokedAt() {
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("old")
            .tokenHash("abc")
            .permissions("[\"pipelines:read\"]")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    when(apiTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
    when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = apiTokenService.revokeToken(TOKEN_ID);

    assertThat(response.revokedAt()).isNotNull();
    verify(apiTokenRepository).save(token);
  }

  @Test
  void revokeToken_rejectsAlreadyRevoked() {
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("old")
            .tokenHash("abc")
            .permissions("[]")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    token.revoke();
    when(apiTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> apiTokenService.revokeToken(TOKEN_ID))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void revokeToken_notFound() {
    when(apiTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> apiTokenService.revokeToken(TOKEN_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void revokeToken_differentTenant_throwsNotFound() {
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(OTHER_TENANT_ID)
            .userId(USER_ID)
            .name("other")
            .tokenHash("abc")
            .permissions("[\"pipelines:read\"]")
            .build();
    when(apiTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> apiTokenService.revokeToken(TOKEN_ID))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("ApiToken");
  }

  @Test
  void listTokens_returnsTenantTokens() {
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("test")
            .tokenHash("abc")
            .permissions("[\"pipelines:read\"]")
            .build();
    when(apiTokenRepository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID))
        .thenReturn(List.of(token));

    var tokens = apiTokenService.listTokens();

    assertThat(tokens).hasSize(1);
    assertThat(tokens.get(0).name()).isEqualTo("test");
  }
}
