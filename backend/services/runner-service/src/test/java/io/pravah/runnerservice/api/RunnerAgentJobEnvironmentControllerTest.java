package io.pravah.runnerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.runnerservice.infrastructure.client.ExecutionJobEnvironmentClient;
import io.pravah.runnerservice.infrastructure.security.RunnerAgentRequestAttributes;
import io.pravah.runnerservice.service.JobAssignmentService;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RunnerAgentJobEnvironmentControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID RUNNER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  private static final UUID JOB_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
  private static final UUID EXECUTION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

  @Mock private ExecutionJobEnvironmentClient executionJobEnvironmentClient;
  @Mock private JobAssignmentService jobAssignmentService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RunnerAgentJobEnvironmentController(
                    executionJobEnvironmentClient, jobAssignmentService))
            .build();
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void resolve_returnsSecretsWhenAssigned() throws Exception {
    when(jobAssignmentService.isAssignedToRunner(JOB_ID, RUNNER_ID)).thenReturn(true);
    when(executionJobEnvironmentClient.resolve(
            eq(TENANT_ID), eq(EXECUTION_ID), eq(JOB_ID), eq(Map.of("API_KEY", "api_key"))))
        .thenReturn(Map.of("API_KEY", "resolved"));

    mockMvc
        .perform(
            post("/api/v1/runners/{runnerId}/jobs/{jobId}/environment-secrets", RUNNER_ID, JOB_ID)
                .requestAttr(RunnerAgentRequestAttributes.RUNNER_ID, RUNNER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "executionId",
                            EXECUTION_ID.toString(),
                            "secretEnvironment",
                            Map.of("API_KEY", "api_key")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.values.API_KEY").value("resolved"));
  }

  @Test
  void resolve_rejectsUnassignedJob() throws Exception {
    when(jobAssignmentService.isAssignedToRunner(JOB_ID, RUNNER_ID)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/runners/{runnerId}/jobs/{jobId}/environment-secrets", RUNNER_ID, JOB_ID)
                .requestAttr(RunnerAgentRequestAttributes.RUNNER_ID, RUNNER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "executionId",
                            EXECUTION_ID.toString(),
                            "secretEnvironment",
                            Map.of("API_KEY", "api_key")))))
        .andExpect(status().isForbidden());

    verify(executionJobEnvironmentClient, never()).resolve(any(), any(), any(), any());
  }
}
