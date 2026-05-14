package io.pravah.pipeline.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.PublishPipelineRequest;
import io.pravah.pipeline.api.dto.UpdatePipelineRequest;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class PipelineLifecycleIT extends AbstractPipelinePostgresIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Test
  void fullLifecycle_createUpdatePublishArchiveListRestore() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreatePipelineRequest createBody =
        new CreatePipelineRequest(projectId, "lifecycle-pipe", "initial", "draftYaml:\n  key: a\n");

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/pipelines")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.currentVersion").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID pipelineId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}", pipelineId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pipelineId.toString()))
        .andExpect(jsonPath("$.status").value("draft"))
        .andExpect(jsonPath("$.currentVersion").value(0))
        .andExpect(jsonPath("$.versions.length()").value(0));

    UpdatePipelineRequest updateBody = new UpdatePipelineRequest(null, "after update", null);
    mockMvc
        .perform(
            put("/api/v1/pipelines/{id}", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("after update"));

    PublishPipelineRequest publishBody =
        new PublishPipelineRequest("stages:\n  - id: extract\n    type: sql\n");
    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/publish", pipelineId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(publishBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.currentVersion").value(1));

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}", pipelineId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.currentVersion").value(1))
        .andExpect(jsonPath("$.versions.length()").value(1))
        .andExpect(jsonPath("$.versions[0].version").value(1));

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}/versions/{version}", pipelineId, 1)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pipelineId").value(pipelineId.toString()))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.definition.stages[0].id").value("extract"));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("status", "draft")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/archive", pipelineId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("archived"));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("status", "archived")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/restore", pipelineId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void getPublishedVersion_unknownVersion_returns404() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}/versions/{version}", UUID.randomUUID(), 99)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPipeline_notFound_returns404() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPipeline_differentTenant_returns404() throws Exception {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();

    String tokenA = testJwtIssuer.generateAccessToken(userA, tenantA);
    String tokenB = testJwtIssuer.generateAccessToken(userB, tenantB);

    CreatePipelineRequest createBody =
        new CreatePipelineRequest(projectId, "iso", null, "key: value\n");

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/pipelines")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createBody)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID pipelineId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/pipelines/{id}", pipelineId).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
  }
}
