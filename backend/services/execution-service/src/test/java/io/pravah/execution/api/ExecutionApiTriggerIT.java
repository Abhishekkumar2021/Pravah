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
import io.pravah.execution.api.dto.TriggerPipelineRunRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.util.List;
import java.util.Map;
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

/** Integration tests for US-03.08 API event trigger. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionApiTriggerIT extends AbstractExecutionPostgresIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private PipelineCatalog pipelineCatalog;

  @Autowired private ExecutionEntityRepository executionEntityRepository;

  @BeforeEach
  void resetPipelineCatalogMock() {
    reset(pipelineCatalog);
  }

  @Test
  void postPipelineRun_returns201WithRunIdAndPersistsParameters() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "extract", "name", "Extract data")));

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(new PublishedPipelineSnapshot(pipelineId, 2, definition, "active"));

    Map<String, Object> parameters = Map.of("env", "prod", "batchSize", 100);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/pipelines/{pipelineId}/runs", pipelineId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new TriggerPipelineRunRequest(null, parameters, false))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pipelineId").value(pipelineId.toString()))
            .andExpect(jsonPath("$.pipelineVersion").value(2))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.jobs.length()").value(1))
            .andReturn();

    UUID executionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.triggerType").value("api"))
        .andExpect(jsonPath("$.triggeredBy").value(userId.toString()));

    var execution = executionEntityRepository.findById(executionId).orElseThrow();
    assertThat(execution.getParameters())
        .containsEntry("env", "prod")
        .containsEntry("batchSize", 100);
  }

  @Test
  void postPipelineRun_async_returns202WithIdOnly() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                1,
                Map.of("stages", List.of(Map.of("id", "only", "name", "Only"))),
                "active"));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{pipelineId}/runs", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TriggerPipelineRunRequest(null, null, true))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.pipelineId").doesNotExist())
        .andExpect(jsonPath("$.jobs").doesNotExist());
  }

  @Test
  void postPipelineRun_emptyBody_returns201() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                1,
                Map.of("stages", List.of(Map.of("id", "only", "name", "Only"))),
                "active"));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{pipelineId}/runs", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("pending"));
  }

  @Test
  void postPipelineRun_inactivePipeline_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                1,
                Map.of("stages", List.of(Map.of("id", "only", "name", "Only"))),
                "draft"));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{pipelineId}/runs", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postPipelineRun_withPipelineVersion_resolvesThatVersion() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), eq(3), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                3,
                Map.of("stages", List.of(Map.of("id", "only", "name", "Only"))),
                "active"));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{pipelineId}/runs", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new TriggerPipelineRunRequest(3, null, false))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pipelineVersion").value(3));
  }
}
