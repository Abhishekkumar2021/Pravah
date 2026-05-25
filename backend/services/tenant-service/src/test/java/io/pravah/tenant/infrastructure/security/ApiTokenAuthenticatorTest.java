package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.tenant.domain.model.ApiToken;
import io.pravah.tenant.domain.repository.ApiTokenRepository;
import io.pravah.tenant.infrastructure.persistence.AuthRlsHelper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticatorTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID TOKEN_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Mock private ApiTokenRepository apiTokenRepository;
  @Mock private AuthRlsHelper authRlsHelper;

  @InjectMocks private ApiTokenAuthenticator authenticator;

  @Test
  void authenticate_validToken_returnsIdentityAndRecordsUsage() {
    String raw = ApiTokenGenerator.generate();
    String hash = ApiTokenHasher.hash(raw);
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("ci")
            .tokenHash(hash)
            .permissions("[\"pipelines:read\"]")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    when(apiTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(apiTokenRepository.save(token)).thenReturn(token);

    var result = authenticator.authenticate(raw);

    assertThat(result).isPresent();
    assertThat(result.get().tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.get().userId()).isEqualTo(USER_ID);
    assertThat(result.get().permissions()).containsExactly("pipelines:read");
    verify(authRlsHelper).enableApiTokenLookup();
    verify(authRlsHelper).disableApiTokenLookup();
    verify(apiTokenRepository).save(token);
    assertThat(token.getLastUsedAt()).isNotNull();
  }

  @Test
  void authenticate_expiredToken_returnsEmpty() {
    String raw = ApiTokenGenerator.generate();
    String hash = ApiTokenHasher.hash(raw);
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("ci")
            .tokenHash(hash)
            .permissions("[\"pipelines:read\"]")
            .expiresAt(Instant.now().minusSeconds(60))
            .build();

    when(apiTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

    assertThat(authenticator.authenticate(raw)).isEmpty();
  }

  @Test
  void authenticate_nonApiTokenPrefix_returnsEmpty() {
    assertThat(authenticator.authenticate("not-a-token")).isEmpty();
  }

  @Test
  void authenticate_revokedToken_returnsEmpty() {
    String raw = ApiTokenGenerator.generate();
    String hash = ApiTokenHasher.hash(raw);
    ApiToken token =
        ApiToken.builder()
            .id(TOKEN_ID)
            .tenantId(TENANT_ID)
            .userId(USER_ID)
            .name("ci")
            .tokenHash(hash)
            .permissions("[\"pipelines:read\"]")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    token.revoke();

    when(apiTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

    assertThat(authenticator.authenticate(raw)).isEmpty();
  }

  @Test
  void authenticate_tokenNotFound_returnsEmpty() {
    String raw = ApiTokenGenerator.generate();
    String hash = ApiTokenHasher.hash(raw);

    when(apiTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

    assertThat(authenticator.authenticate(raw)).isEmpty();
    verify(authRlsHelper).enableApiTokenLookup();
    verify(authRlsHelper).disableApiTokenLookup();
  }
}
