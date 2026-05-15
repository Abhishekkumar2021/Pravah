package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import io.pravah.test.security.TestJwtIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ApiTenantJwtFilterTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private JwtTokenVerifier jwtTokenVerifier;

  private ApiTenantJwtFilter apiTenantJwtFilter;
  private TestJwtIssuer testJwtIssuer;

  @BeforeEach
  void setUp() {
    testJwtIssuer = new TestJwtIssuer();
    apiTenantJwtFilter = new ApiTenantJwtFilter(jwtTokenVerifier);
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
    assertThat(apiTenantJwtFilter.shouldNotFilter(request)).isTrue();

    when(request.getRequestURI()).thenReturn("/actuator/info");
    assertThat(apiTenantJwtFilter.shouldNotFilter(request)).isTrue();

    when(request.getRequestURI()).thenReturn("/");
    assertThat(apiTenantJwtFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldNotFilter_apiPath_returnsFalse() {
    when(request.getRequestURI()).thenReturn("/api/v1/pipelines");
    assertThat(apiTenantJwtFilter.shouldNotFilter(request)).isFalse();

    when(request.getRequestURI()).thenReturn("/api/v1/users");
    assertThat(apiTenantJwtFilter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void doFilterInternal_validToken_setsTenantContextAndContinues() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);
    JwtClaims claims =
        new JwtClaims(userId, tenantId, "jti", Instant.now(), Instant.now().plusSeconds(900));

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtTokenVerifier.validateAndGetClaims(token)).thenReturn(claims);

    apiTenantJwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never())
        .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
  }

  @Test
  void doFilterInternal_missingAuthorizationHeader_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    apiTenantJwtFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_emptyBearerToken_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer ");

    apiTenantJwtFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_invalidToken_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
    when(jwtTokenVerifier.validateAndGetClaims("invalid.token.here"))
        .thenThrow(new JwtVerificationException("Invalid token"));

    apiTenantJwtFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_wrongAuthorizationScheme_returns401() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    apiTenantJwtFilter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_clearsContextInFinally() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);
    JwtClaims claims =
        new JwtClaims(userId, tenantId, "jti", Instant.now(), Instant.now().plusSeconds(900));

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtTokenVerifier.validateAndGetClaims(token)).thenReturn(claims);

    FilterChain throwingChain =
        mock(
            FilterChain.class,
            invocation -> {
              assertThat(TenantContext.getCurrentTenantId()).isEqualTo(tenantId);
              assertThat(TenantContext.getCurrentUserId()).isEqualTo(userId);
              throw new RuntimeException("Test exception");
            });

    try {
      apiTenantJwtFilter.doFilterInternal(request, response, throwingChain);
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
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);
    JwtClaims claims =
        new JwtClaims(userId, tenantId, "jti", Instant.now(), Instant.now().plusSeconds(900));

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtTokenVerifier.validateAndGetClaims(token)).thenReturn(claims);

    FilterChain verifyingChain =
        (req, res) -> {
          var auth = SecurityContextHolder.getContext().getAuthentication();
          assertThat(auth).isNotNull();
          assertThat(auth.getPrincipal()).isEqualTo(userId.toString());
          assertThat(auth.getAuthorities()).hasSize(1);
          assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        };

    apiTenantJwtFilter.doFilterInternal(request, response, verifyingChain);
  }
}
