package io.pravah.pipeline.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

  private static final String SECRET =
      "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  private JwtTokenProvider jwtTokenProvider;
  private TenantFilter tenantFilter;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(SECRET, 3600000, 86400000);
    tenantFilter = new TenantFilter(jwtTokenProvider);
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotFilter_nonApiPath_returnsTrue() {
    when(request.getRequestURI()).thenReturn("/actuator/health");
    assertThat(tenantFilter.shouldNotFilter(request)).isTrue();

    when(request.getRequestURI()).thenReturn("/actuator/info");
    assertThat(tenantFilter.shouldNotFilter(request)).isTrue();

    when(request.getRequestURI()).thenReturn("/");
    assertThat(tenantFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldNotFilter_apiPath_returnsFalse() {
    when(request.getRequestURI()).thenReturn("/api/v1/pipelines");
    assertThat(tenantFilter.shouldNotFilter(request)).isFalse();

    when(request.getRequestURI()).thenReturn("/api/v1/users");
    assertThat(tenantFilter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void doFilterInternal_validToken_setsTenantContextAndContinues() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never())
        .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
  }

  @Test
  void doFilterInternal_missingAuthorizationHeader_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_emptyBearerToken_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer ");

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_invalidToken_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_tokenWithoutTenantId_returns401() throws Exception {
    String refreshToken = jwtTokenProvider.generateRefreshToken(UUID.randomUUID());

    when(request.getHeader("Authorization")).thenReturn("Bearer " + refreshToken);

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(response)
        .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token missing tenant_id claim");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_wrongAuthorizationScheme_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    tenantFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_clearsContextInFinally() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    FilterChain throwingChain =
        mock(
            FilterChain.class,
            invocation -> {
              assertThat(TenantContext.getCurrentTenantId()).isEqualTo(tenantId);
              assertThat(TenantContext.getCurrentUserId()).isEqualTo(userId);
              throw new RuntimeException("Test exception");
            });

    try {
      tenantFilter.doFilterInternal(request, response, throwingChain);
    } catch (RuntimeException e) {
      // Expected
    }

    assertThat(TenantContext.getCurrentTenantId()).isNull();
    assertThat(TenantContext.getCurrentUserId()).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doFilterInternal_setsSecurityContextAuthentication() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "e@e.com", "Name");

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    FilterChain verifyingChain =
        (req, res) -> {
          var auth = SecurityContextHolder.getContext().getAuthentication();
          assertThat(auth).isNotNull();
          assertThat(auth.getPrincipal()).isEqualTo(userId.toString());
          assertThat(auth.getAuthorities()).hasSize(1);
          assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        };

    tenantFilter.doFilterInternal(request, response, verifyingChain);
  }
}
