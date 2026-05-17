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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for stage output resolution (US-02.10).
 *
 * <p>Tests end-to-end execution of pipelines where downstream stages reference upstream stage
 * outputs using {@code ${stages.stageId.output.key}} syntax.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
@DisplayName("Stage Output Resolution IT")
class StageOutputResolutionIT extends AbstractExecutionPostgresIT {

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
  @DisplayName("Downstream stage can access upstream SQL stage row_count")
  void stageOutput_accessRowCount_fromUpstreamSqlStage() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> extractStage = new LinkedHashMap<>();
    extractStage.put("id", "extract");
    extractStage.put("name", "Extract Data");
    extractStage.put("type", "sql");
    extractStage.put("config", Map.of("query", "SELECT 1 AS id, 'Alice' AS name"));

    Map<String, Object> notifyStage = new LinkedHashMap<>();
    notifyStage.put("id", "notify");
    notifyStage.put("name", "Notify Result");
    notifyStage.put("dependsOn", List.of("extract"));
    notifyStage.put(
        "config", Map.of("message", "Extracted ${stages.extract.output.row_count} rows"));

    Map<String, Object> definition = Map.of("stages", List.of(extractStage, notifyStage));

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

    JobEntity extractJob =
        jobs.stream().filter(j -> "extract".equals(j.getStageId())).findFirst().orElseThrow();
    JobEntity notifyJob =
        jobs.stream().filter(j -> "notify".equals(j.getStageId())).findFirst().orElseThrow();

    assertThat(extractJob.getOutput()).containsEntry("row_count", 1);
    assertThat(notifyJob.getOutput()).containsEntry("executor", "embedded-echo");
    assertThat(notifyJob.getOutput().get("message").toString()).contains("1 rows");
  }

  @Test
  @DisplayName("Downstream stage can access nested output path (preview.0.id)")
  @SuppressWarnings("unchecked")
  void stageOutput_accessNestedPath_fromSqlPreview() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> queryStage = new LinkedHashMap<>();
    queryStage.put("id", "query");
    queryStage.put("name", "Query Data");
    queryStage.put("type", "sql");
    queryStage.put("config", Map.of("query", "SELECT 42 AS value, 'test' AS label"));

    Map<String, Object> echoStage = new LinkedHashMap<>();
    echoStage.put("id", "echo");
    echoStage.put("name", "Echo Result");
    echoStage.put("dependsOn", List.of("query"));
    echoStage.put(
        "config", Map.of("message", "First value: ${stages.query.output.preview.0.value}"));

    Map<String, Object> definition = Map.of("stages", List.of(queryStage, echoStage));

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
    processAllPendingJobCreated(tenantId);

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);

    assertThat(execution.getStatus()).isEqualTo(ExecutionState.SUCCEEDED);

    JobEntity queryJob =
        jobs.stream().filter(j -> "query".equals(j.getStageId())).findFirst().orElseThrow();
    JobEntity echoJob =
        jobs.stream().filter(j -> "echo".equals(j.getStageId())).findFirst().orElseThrow();

    List<Map<String, Object>> preview =
        (List<Map<String, Object>>) queryJob.getOutput().get("preview");
    assertThat(preview).isNotEmpty();
    assertThat(preview.get(0)).containsEntry("value", 42);

    assertThat(echoJob.getOutput().get("message").toString()).contains("42");
  }

  @Test
  @DisplayName("Three-stage pipeline with chained output references")
  void stageOutput_threeStageChain_resolvesCorrectly() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> stage1 = new LinkedHashMap<>();
    stage1.put("id", "stage1");
    stage1.put("name", "Stage 1");
    stage1.put("type", "sql");
    stage1.put("config", Map.of("query", "SELECT 10 AS count"));

    Map<String, Object> stage2 = new LinkedHashMap<>();
    stage2.put("id", "stage2");
    stage2.put("name", "Stage 2");
    stage2.put("type", "sql");
    stage2.put("dependsOn", List.of("stage1"));
    stage2.put("config", Map.of("query", "SELECT 20 AS count"));

    Map<String, Object> stage3 = new LinkedHashMap<>();
    stage3.put("id", "stage3");
    stage3.put("name", "Stage 3");
    stage3.put("dependsOn", List.of("stage2"));
    stage3.put(
        "config",
        Map.of(
            "message",
            "Stage1: ${stages.stage1.output.row_count}, Stage2: ${stages.stage2.output.row_count}"));

    Map<String, Object> definition = Map.of("stages", List.of(stage1, stage2, stage3));

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
    processAllPendingJobCreated(tenantId);
    processAllPendingJobCreated(tenantId);

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    assertThat(execution.getStatus()).isEqualTo(ExecutionState.SUCCEEDED);

    JobEntity stage3Job =
        jobEntityRepository.findByExecutionIdAndStageId(executionId, "stage3").orElseThrow();

    String message = stage3Job.getOutput().get("message").toString();
    assertThat(message).contains("Stage1: 1").contains("Stage2: 1");
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
