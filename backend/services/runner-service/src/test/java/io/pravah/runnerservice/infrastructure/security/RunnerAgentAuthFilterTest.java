package io.pravah.runnerservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.service.RunnerService;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import java.util.Optional;
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
class RunnerAgentAuthFilterTest {

  private static final UUID RUNNER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  private static final UUID JOB_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @Mock private RunnerService runnerService;
  @Mock private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotFilter_nonPostRequests() {
    var filter = new RunnerAgentAuthFilter(runnerService);
    var request =
        new MockHttpServletRequest(
            "GET", "/api/v1/runners/" + RUNNER_ID + "/jobs/" + JOB_ID + "/environment-secrets");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void doFilterInternal_validToken_setsTenantAndRunnerAttribute() throws Exception {
    var filter = new RunnerAgentAuthFilter(runnerService);
    Runner runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setTenantId(TENANT_ID);
    when(runnerService.validateToken(RUNNER_ID, "stream-token")).thenReturn(Optional.of(runner));

    var request =
        new MockHttpServletRequest(
            "POST", "/api/v1/runners/" + RUNNER_ID + "/jobs/" + JOB_ID + "/environment-secrets");
    request.addHeader("Authorization", "Bearer stream-token");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID);
          assertThat(req.getAttribute(RunnerAgentRequestAttributes.RUNNER_ID)).isEqualTo(RUNNER_ID);
          assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        });

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilterInternal_invalidToken_returns401() throws Exception {
    var filter = new RunnerAgentAuthFilter(runnerService);
    when(runnerService.validateToken(RUNNER_ID, "bad")).thenReturn(Optional.empty());

    var request =
        new MockHttpServletRequest(
            "POST", "/api/v1/runners/" + RUNNER_ID + "/jobs/" + JOB_ID + "/environment-secrets");
    request.addHeader("Authorization", "Bearer bad");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
  }
}
