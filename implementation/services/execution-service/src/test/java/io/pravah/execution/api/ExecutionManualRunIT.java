package io.pravah.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionManualRunIT extends AbstractExecutionPostgresIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private PipelineCatalog pipelineCatalog;

  @Autowired private OutboxRepository outboxRepository;

  @Autowired private ExecutionEntityRepository executionEntityRepository;

  @Autowired private JobEntityRepository jobEntityRepository;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void resetPipelineCatalogMock() {
    reset(pipelineCatalog);
  }

  @Test
  void postManualExecution_thenGet_returns201And200() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract data"),
                Map.of("id", "load", "name", "Load")));

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
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pipelineId").value(pipelineId.toString()))
            .andExpect(jsonPath("$.pipelineVersion").value(1))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.jobs.length()").value(2))
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    assertThat(outboxRepository.findAll())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getEventType()).isEqualTo(ExecutionEventTypes.EXECUTION_CREATED);
              assertThat(row.getAggregateId()).isEqualTo(executionId);
              assertThat(row.getTopic()).isEqualTo("pravah.execution.execution.events");
              assertThat(row.getPartitionKey()).isEqualTo(executionId.toString());
              assertThat(row.getPublishedAt()).isNull();
            });

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(executionId.toString()))
        .andExpect(jsonPath("$.pipelineId").value(pipelineId.toString()))
        .andExpect(jsonPath("$.triggerType").value("manual"))
        .andExpect(jsonPath("$.triggeredBy").value(userId.toString()))
        .andExpect(jsonPath("$.jobs.length()").value(2));
  }

  @Test
  void postManualExecution_pipelineNotActive_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId, 1, Map.of("stages", List.of(Map.of("id", "a", "name", "A"))), "draft"));

    mockMvc
        .perform(
            post("/api/v1/executions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new CreateExecutionRequest(pipelineId, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postManualExecution_noStages_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(new PublishedPipelineSnapshot(pipelineId, 1, Map.of(), "active"));

    mockMvc
        .perform(
            post("/api/v1/executions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new CreateExecutionRequest(pipelineId, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getExecution_otherTenant_returns404() throws Exception {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String tokenA = testJwtIssuer.generateAccessToken(userA, tenantA);
    String tokenB = testJwtIssuer.generateAccessToken(userB, tenantB);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                1,
                Map.of("stages", List.of(Map.of("id", "only", "name", "Only"))),
                "active"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/executions")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    assertThat(executionId).isNotNull();
  }

  @Test
  void postCancel_whilePending_cancelsExecutionAndJobs_andWritesOutbox() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract data"),
                Map.of("id", "load", "name", "Load")));

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
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/executions/{id}/cancel", executionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.jobs[0].status").value("cancelled"))
        .andExpect(jsonPath("$.jobs[1].status").value("cancelled"));

    assertThat(outboxRepository.findAll())
        .filteredOn(o -> ExecutionEventTypes.EXECUTION_CANCELLED.equals(o.getEventType()))
        .hasSize(1);
  }

  @Test
  void postCancel_whenSucceeded_returns409() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "only", "name", "Only")));

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
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement("UPDATE executions SET status = ?, completed_at = ? WHERE id = ?")) {
      ps.setString(1, ExecutionState.SUCCEEDED.name());
      ps.setTimestamp(2, Timestamp.from(Instant.now()));
      ps.setObject(3, executionId);
      assertThat(ps.executeUpdate()).isEqualTo(1);
    }

    mockMvc
        .perform(
            post("/api/v1/executions/{id}/cancel", executionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void postCancel_whileRunning_cancelsRemainingJobs() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract data"),
                Map.of("id", "load", "name", "Load")));

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
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    JobEntity first =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId).getFirst();
    first.queue();
    first.assign(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    jobEntityRepository.saveAndFlush(first);

    ExecutionEntity execution = executionEntityRepository.findById(executionId).orElseThrow();
    execution.start();
    executionEntityRepository.saveAndFlush(execution);

    mockMvc
        .perform(
            post("/api/v1/executions/{id}/cancel", executionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
    assertThat(jobs).allMatch(j -> j.getStatus() == JobState.CANCELLED);
  }

  @Test
  void postCancel_twiceSecondCallIsIdempotent() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "only", "name", "Only")));

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
                            new CreateExecutionRequest(pipelineId, null))))
            .andExpect(status().isCreated())
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/executions/{id}/cancel", executionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/executions/{id}/cancel", executionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    assertThat(outboxRepository.findAll())
        .filteredOn(o -> ExecutionEventTypes.EXECUTION_CANCELLED.equals(o.getEventType()))
        .hasSize(1);
  }

  @Test
  void getExecution_unknownId_returns404() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @TestConfiguration
  static class PipelineCatalogTestConfig {

    @Bean
    @Primary
    PipelineCatalog pipelineCatalog() {
      return mock(PipelineCatalog.class);
    }
  }
}
