package io.pravah.execution.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pravah.spring.security.JwtTokenVerifier;
import io.pravah.spring.security.JwtTokenVerifier.JwtClaims;
import io.pravah.spring.security.JwtTokenVerifier.JwtVerificationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class JwtWebSocketHandshakeInterceptorTest {

  @Mock private JwtTokenVerifier jwtTokenVerifier;

  private JwtWebSocketHandshakeInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new JwtWebSocketHandshakeInterceptor(jwtTokenVerifier);
  }

  @Test
  void beforeHandshake_validAccessTokenQuery_setsTenantAndUser() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(jwtTokenVerifier.validateAndGetClaims("good-token"))
        .thenReturn(
            new JwtClaims(userId, tenantId, "jti", Instant.now(), Instant.now().plusSeconds(900)));

    MockHttpServletRequest servlet = new MockHttpServletRequest();
    servlet.setParameter("access_token", "good-token");
    Map<String, Object> attributes = new HashMap<>();

    boolean allowed =
        interceptor.beforeHandshake(new ServletServerHttpRequest(servlet), null, null, attributes);

    assertThat(allowed).isTrue();
    assertThat(attributes.get(JwtWebSocketHandshakeInterceptor.ATTR_TENANT_ID)).isEqualTo(tenantId);
    assertThat(attributes.get(JwtWebSocketHandshakeInterceptor.ATTR_USER_ID)).isEqualTo(userId);
  }

  @Test
  void beforeHandshake_missingToken_rejects() {
    Map<String, Object> attributes = new HashMap<>();
    boolean allowed =
        interceptor.beforeHandshake(
            new ServletServerHttpRequest(new MockHttpServletRequest()), null, null, attributes);
    assertThat(allowed).isFalse();
    assertThat(attributes).isEmpty();
  }

  @Test
  void beforeHandshake_invalidToken_rejects() {
    when(jwtTokenVerifier.validateAndGetClaims("bad"))
        .thenThrow(new JwtVerificationException("invalid"));

    MockHttpServletRequest servlet = new MockHttpServletRequest();
    servlet.setParameter("access_token", "bad");
    Map<String, Object> attributes = new HashMap<>();

    boolean allowed =
        interceptor.beforeHandshake(new ServletServerHttpRequest(servlet), null, null, attributes);

    assertThat(allowed).isFalse();
    assertThat(attributes).isEmpty();
  }
}
