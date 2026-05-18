package io.pravah.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.application.ExecutionCreatedProcessingService;
import io.pravah.execution.application.JobCreatedProcessingService;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for Python stage execution (US-02.15).
 *
 * <p>Requires {@code python3} on the host ({@code PRAVAH_PYTHON_ENABLED=true}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
@EnabledIf("io.pravah.test.PythonConditions#isPythonAvailable")
class PythonStageExecutionIT extends AbstractExecutionPostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer testJwtIssuer;
  @Autowired private PipelineCatalog pipelineCatalog;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private ExecutionEntityRepository executionEntityRepository;
  @Autowired private JobEntityRepository jobEntityRepository;
  @Autowired private ExecutionCreatedProcessingService executionCreatedProcessingService;
  @Autowired private JobCreatedProcessingService jobCreatedProcessingService;

  private final Set<UUID> processedJobEventIds = new HashSet<>();

  @BeforeEach
  void resetMocks() {
    reset(pipelineCatalog);
    processedJobEventIds.clear();
  }

  @Test
  void pythonStage_inlineScript_succeedsWithStructuredOutput() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    String script = "import json\nprint(json.dumps({\"message\": \"hello-pravah\"}))\n";

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "py-stage",
                    "name",
                    "Python Stage",
                    "type",
                    "python",
                    "config",
                    Map.of("script", script))));

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(new PublishedPipelineSnapshot(pipelineId, 1, definition, "active"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/executions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateExecutionRequest(pipelineId, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobs.length()").value(1))
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    processExecutionCreated(tenantId);
    processAllPendingJobCreated(tenantId);

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);

    assertThat(execution.getStatus()).isEqualTo(ExecutionState.SUCCEEDED);
    assertThat(jobs).hasSize(1);
    assertThat(jobs.get(0).getStatus()).isEqualTo(JobState.SUCCEEDED);
    assertThat(jobs.get(0).getOutput()).containsEntry("executor", "python");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) jobs.get(0).getOutput().get("result");
    assertThat(result).containsEntry("message", "hello-pravah");
  }

  private void processExecutionCreated(UUID tenantId) {
    OutboxEntity executionCreatedEntry =
        outboxRepository.findAll().stream()
            .filter(o -> ExecutionEventTypes.EXECUTION_CREATED.equals(o.getEventType()))
            .findFirst()
            .orElseThrow();

    try {
      TenantContext.setCurrentTenantId(tenantId);
      executionCreatedProcessingService.processExecutionCreated(executionCreatedEntry.getPayload());
    } finally {
      TenantContext.clear();
    }
  }

  private void processAllPendingJobCreated(UUID tenantId) {
    List<OutboxEntity> pendingJobs =
        outboxRepository.findAll().stream()
            .filter(o -> JobEventTypes.JOB_CREATED.equals(o.getEventType()))
            .filter(
                o ->
                    !processedJobEventIds.contains(
                        UUID.fromString(o.getPayload().get("eventId").toString())))
            .toList();

    for (OutboxEntity outboxEntry : pendingJobs) {
      UUID eventId = UUID.fromString(outboxEntry.getPayload().get("eventId").toString());
      processedJobEventIds.add(eventId);

      try {
        TenantContext.setCurrentTenantId(tenantId);
        jobCreatedProcessingService.processJobCreated(outboxEntry.getPayload());
      } finally {
        TenantContext.clear();
      }
    }
  }
}
