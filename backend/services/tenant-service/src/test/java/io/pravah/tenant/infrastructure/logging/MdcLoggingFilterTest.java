package io.pravah.tenant.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private FilterChain filterChain;

  private final MdcLoggingFilter filter = new MdcLoggingFilter();

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    MDC.clear();
  }

  @Test
  void doFilterInternal_populatesMdcAndRequestIdHeader() throws Exception {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);

    var request = new MockHttpServletRequest("GET", "/api/v1/users");
    request.addHeader("X-Request-Id", "req-123");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("tenant_id")).isEqualTo(TENANT_ID.toString());
          assertThat(MDC.get("user_id")).isEqualTo(USER_ID.toString());
          assertThat(MDC.get("request_id")).isEqualTo("req-123");
        });

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
    assertThat(MDC.get("tenant_id")).isNull();
  }

  @Test
  void doFilterInternal_generatesRequestIdWhenMissing() throws Exception {
    var request = new MockHttpServletRequest("GET", "/health");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
  }
}
