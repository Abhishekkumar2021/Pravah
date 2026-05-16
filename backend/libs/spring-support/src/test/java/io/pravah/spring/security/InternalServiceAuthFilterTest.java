package io.pravah.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class InternalServiceAuthFilterTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final String SECRET = "test-internal-secret";

  @Mock private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotFilter_publicPaths() {
    var filter = new InternalServiceAuthFilter(SECRET);
    var request = new MockHttpServletRequest("GET", "/api/v1/pipelines");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void doFilterInternal_validSecret_setsTenantContext() throws Exception {
    var filter = new InternalServiceAuthFilter(SECRET);
    var request = new MockHttpServletRequest("POST", "/api/v1/internal/executions");
    request.addHeader(InternalServiceAuthFilter.SECRET_HEADER, SECRET);
    request.addHeader(InternalServiceAuthFilter.TENANT_HEADER, TENANT_ID.toString());
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
  void doFilterInternal_wrongSecret_returns401() throws Exception {
    var filter = new InternalServiceAuthFilter(SECRET);
    var request = new MockHttpServletRequest("POST", "/api/v1/internal/executions");
    request.addHeader(InternalServiceAuthFilter.SECRET_HEADER, "wrong");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void doFilterInternal_missingTenantHeader_returns400() throws Exception {
    var filter = new InternalServiceAuthFilter(SECRET);
    var request = new MockHttpServletRequest("POST", "/api/v1/internal/executions");
    request.addHeader(InternalServiceAuthFilter.SECRET_HEADER, SECRET);
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(400);
  }
}
