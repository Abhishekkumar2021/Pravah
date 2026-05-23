package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private JwtTokenVerifier jwtTokenVerifier;
  @Mock private ApiTokenAuthenticator apiTokenAuthenticator;
  @Mock private FilterChain filterChain;

  private TenantFilter filter;

  @BeforeEach
  void setUp() {
    filter =
        new TenantFilter(
            jwtTokenVerifier,
            apiTokenAuthenticator,
            new io.pravah.spring.security.OptionalJwtBlocklistChecker(null));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilterInternal_noAuthorization_continuesWithoutContext() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/users");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(TenantContext.getCurrentTenantId()).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doFilterInternal_validJwt_setsTenantAndUser() throws Exception {
    String jwt = "ey.test.token";
    when(jwtTokenVerifier.validateAndGetClaims(jwt))
        .thenReturn(
            new JwtClaims(
                USER_ID,
                TENANT_ID,
                "jti",
                Instant.now(),
                Instant.now().plusSeconds(900),
                List.of("editor"),
                List.of("users:read")));

    var request = new MockHttpServletRequest("GET", "/api/v1/users");
    request.addHeader("Authorization", "Bearer " + jwt);
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID);
          assertThat(TenantContext.getCurrentUserId()).isEqualTo(USER_ID);
          assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        });
  }

  @Test
  void doFilterInternal_invalidJwt_continuesWithoutAuthentication() throws Exception {
    when(jwtTokenVerifier.validateAndGetClaims(anyString()))
        .thenThrow(new JwtVerificationException("bad token"));

    var request = new MockHttpServletRequest("GET", "/api/v1/users");
    request.addHeader("Authorization", "Bearer bad");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doFilterInternal_apiToken_setsContext() throws Exception {
    String raw = ApiTokenGenerator.generate();
    when(apiTokenAuthenticator.authenticate(raw))
        .thenReturn(
            Optional.of(
                new ApiTokenAuthenticator.AuthenticatedApiToken(
                    UUID.randomUUID(), TENANT_ID, USER_ID, List.of("pipelines:read"))));

    var request = new MockHttpServletRequest("GET", "/api/v1/pipelines");
    request.addHeader("Authorization", "Bearer " + raw);
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID);
          assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        });
  }

  @Test
  void doFilterInternal_clearsContextInFinally() throws Exception {
    var request = new MockHttpServletRequest("GET", "/health");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(TenantContext.getCurrentTenantId()).isNull();
  }
}
