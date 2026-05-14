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
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
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
