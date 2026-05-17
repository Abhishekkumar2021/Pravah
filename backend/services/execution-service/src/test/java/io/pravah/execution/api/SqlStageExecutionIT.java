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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for SQL stage execution (US-02.14).
 *
 * <p>Tests end-to-end execution of pipelines with SQL stages, verifying:
 *
 * <ul>
 *   <li>SQL stages are correctly routed to SqlEmbeddedStageExecutor
 *   <li>Successful queries produce proper output
 *   <li>Failed queries properly record errors
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class SqlStageExecutionIT extends AbstractExecutionPostgresIT {

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
  void sqlStage_successfulQuery_executesAndReturnsResults() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "sql-stage",
                    "name", "SQL Query Stage",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1 AS result"))));

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
            .andExpect(jsonPath("$.status").value("pending"))
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
    assertThat(jobs.get(0).getOutput()).containsEntry("executor", "sql");
    assertThat(jobs.get(0).getOutput()).containsEntry("row_count", 1);
    assertThat(jobs.get(0).getOutput()).containsEntry("query_type", "SELECT");
    assertThat(jobs.get(0).getOutput()).containsKey("columns");
    assertThat(jobs.get(0).getOutput()).containsKey("preview");
  }

  @Test
  @SuppressWarnings("unchecked")
  void sqlStage_queryWithVariables_substitutesCorrectly() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("table_suffix", Map.of("type", "string", "default", "default")),
            "stages",
            List.of(
                Map.of(
                    "id", "sql-stage",
                    "name", "SQL Query Stage",
                    "type", "sql",
                    "config", Map.of("query", "SELECT '${var.table_suffix}' AS suffix"))));

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
                            new CreateExecutionRequest(
                                pipelineId, null, Map.of("table_suffix", "prod")))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    List<Map<String, Object>> stages =
        (List<Map<String, Object>>) execution.getDefinitionSnapshot().get("stages");
    Map<String, Object> config = (Map<String, Object>) stages.get(0).get("config");

    assertThat(config.get("query")).isEqualTo("SELECT 'prod' AS suffix");
  }

  @Test
  void sqlStage_invalidQuery_failsWithError() throws Exception {
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
                Map.of(
                    "id", "sql-stage",
                    "name", "SQL Query Stage",
                    "type", "sql",
                    "config", Map.of("query", "SELECTT INVALID SYNTAX"))));

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
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    processExecutionCreated(tenantId);
    processAllPendingJobCreated(tenantId);

    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();

    assertThat(jobs.get(0).getStatus()).isEqualTo(JobState.FAILED);
    assertThat(execution.getStatus()).isEqualTo(ExecutionState.FAILED);
    assertThat(jobs.get(0).getOutput()).containsKey("error");
    assertThat(jobs.get(0).getOutput()).containsKey("sql_state");
  }

  @Test
  void echoStage_noType_fallsBackToEchoExecutor() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "echo-stage",
                    "name", "Echo Stage")));

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
    assertThat(jobs.get(0).getOutput()).containsEntry("executor", "embedded-echo");
  }

  @Test
  void mixedPipeline_sqlAndEcho_executesInSequence() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id", "sql-extract",
                    "name", "Extract Data",
                    "type", "sql",
                    "config", Map.of("query", "SELECT 1 AS num")),
                Map.of(
                    "id", "echo-transform",
                    "name", "Transform",
                    "depends_on", List.of("sql-extract"))));

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
            .andExpect(jsonPath("$.jobs.length()").value(2))
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    processExecutionCreated(tenantId);

    processAllPendingJobCreated(tenantId);

    processAllPendingJobCreated(tenantId);

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);

    assertThat(execution.getStatus()).isEqualTo(ExecutionState.SUCCEEDED);
    assertThat(jobs).hasSize(2);
    assertThat(jobs).allMatch(j -> j.getStatus() == JobState.SUCCEEDED);

    JobEntity sqlJob =
        jobs.stream().filter(j -> "sql-extract".equals(j.getStageId())).findFirst().orElseThrow();
    JobEntity echoJob =
        jobs.stream()
            .filter(j -> "echo-transform".equals(j.getStageId()))
            .findFirst()
            .orElseThrow();

    assertThat(sqlJob.getOutput()).containsEntry("executor", "sql");
    assertThat(echoJob.getOutput()).containsEntry("executor", "embedded-echo");
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
