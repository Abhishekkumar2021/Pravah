package io.pravah.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.RetryExecutionRequest;
import io.pravah.execution.application.ExecutionCreatedProcessingService;
import io.pravah.execution.application.JobCreatedProcessingService;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.CheckpointRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests for retry-from-stage and checkpoints (US-02.05 / US-02.12). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionRetryIT extends AbstractExecutionPostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer testJwtIssuer;
  @Autowired private PipelineCatalog pipelineCatalog;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private ExecutionEntityRepository executionEntityRepository;
  @Autowired private JobEntityRepository jobEntityRepository;
  @Autowired private CheckpointRepository checkpointRepository;
  @Autowired private ExecutionCreatedProcessingService executionCreatedProcessingService;
  @Autowired private JobCreatedProcessingService jobCreatedProcessingService;

  private final Set<UUID> processedJobEventIds = new HashSet<>();

  @BeforeEach
  void resetMocks() {
    reset(pipelineCatalog);
    processedJobEventIds.clear();
  }

  @Test
  void retryFromFailedStage_restoresUpstreamAndRequeuesFailed() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "retry",
            Map.of("max_attempts", 1),
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract"),
                Map.of(
                    "id",
                    "load",
                    "name",
                    "Load",
                    "dependsOn",
                    List.of("extract"),
                    "simulate_exit_code",
                    1)));

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(new PublishedPipelineSnapshot(pipelineId, 1, definition, "active"));

    UUID sourceExecutionId = startExecution(token, pipelineId);
    processExecutionCreated(tenantId);
    drainPendingJobsUntilTerminal(tenantId, sourceExecutionId);

    ExecutionEntity source = executionEntityRepository.findById(sourceExecutionId).orElseThrow();
    assertThat(source.getStatus()).isEqualTo(ExecutionState.FAILED);

    List<JobEntity> sourceJobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(sourceExecutionId);
    JobEntity extract =
        sourceJobs.stream().filter(j -> "extract".equals(j.getStageId())).findFirst().orElseThrow();
    JobEntity load =
        sourceJobs.stream().filter(j -> "load".equals(j.getStageId())).findFirst().orElseThrow();
    assertThat(extract.getStatus()).isEqualTo(JobState.SUCCEEDED);
    assertThat(load.getStatus()).isEqualTo(JobState.FAILED);
    assertThat(checkpointRepository.findByExecutionId(sourceExecutionId)).hasSize(1);

    MvcResult retryResult =
        mockMvc
            .perform(
                post("/api/v1/executions/{id}/retry", sourceExecutionId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RetryExecutionRequest("load"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.jobs[?(@.stageId=='extract')].status").value("succeeded"))
            .andExpect(jsonPath("$.jobs[?(@.stageId=='load')].status").value("queued"))
            .andReturn();

    UUID retryExecutionId =
        UUID.fromString(
            objectMapper
                .readTree(retryResult.getResponse().getContentAsString())
                .get("id")
                .asText());

    ExecutionEntity retryExecution =
        executionEntityRepository.findById(retryExecutionId).orElseThrow();
    assertThat(retryExecution.getRetryOf()).isEqualTo(sourceExecutionId);

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", sourceExecutionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("retrying"))
        .andExpect(jsonPath("$.retryCount").value(1));

    processAllPendingJobCreated(tenantId);

    List<JobEntity> retryJobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(retryExecutionId);
    assertThat(retryJobs.stream().filter(j -> "extract".equals(j.getStageId())).findFirst())
        .get()
        .extracting(JobEntity::getStatus)
        .isEqualTo(JobState.SUCCEEDED);
    assertThat(retryJobs.stream().filter(j -> "load".equals(j.getStageId())).findFirst())
        .get()
        .extracting(JobEntity::getStatus)
        .isIn(JobState.FAILED, JobState.QUEUED, JobState.RUNNING);
  }

  private UUID startExecution(String token, UUID pipelineId) throws Exception {
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
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
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

  private void drainPendingJobsUntilTerminal(UUID tenantId, UUID executionId) {
    for (int round = 0; round < 20; round++) {
      processAllPendingJobCreated(tenantId);
      ExecutionState status =
          executionEntityRepository.findById(executionId).orElseThrow().getStatus();
      if (status == ExecutionState.FAILED
          || status == ExecutionState.SUCCEEDED
          || status == ExecutionState.CANCELLED) {
        return;
      }
    }
    throw new IllegalStateException("Execution did not reach a terminal state");
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
