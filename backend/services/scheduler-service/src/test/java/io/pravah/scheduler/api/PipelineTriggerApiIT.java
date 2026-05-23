package io.pravah.scheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.api.dto.CreatePipelineTriggerRequest;
import io.pravah.scheduler.api.dto.UpdatePipelineTriggerRequest;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class PipelineTriggerApiIT extends AbstractSchedulerPostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer jwtIssuer;
  @Autowired private DataSource dataSource;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private String jwt;

  @BeforeEach
  void setUp() {
    jwt = jwtIssuer.generateAccessToken(userId, tenantId);
  }

  @AfterEach
  void tearDown() throws SQLException {
    TenantContext.clear();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM trigger_dispatch_history");
      stmt.execute("DELETE FROM trigger_dispatch_pending");
      stmt.execute("DELETE FROM kafka_trigger_processed");
      stmt.execute("DELETE FROM pipeline_triggers");
    }
  }

  @Test
  void createWebhookTrigger_returnsSecretAndUrl() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, "webhook-hook", "webhook", Map.of());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/triggers")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("webhook-hook"))
            .andExpect(jsonPath("$.triggerType").value("webhook"))
            .andExpect(jsonPath("$.webhookUrl").isNotEmpty())
            .andExpect(jsonPath("$.webhookSecret").isNotEmpty())
            .andExpect(jsonPath("$.enabled").value(true))
            .andReturn();

    String triggerId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    assertThat(triggerId).isNotBlank();
  }

  @Test
  void createKafkaTrigger_requiresTopic() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, "kafka-trigger", "kafka", Map.of());

    mockMvc
        .perform(
            post("/api/v1/triggers")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createKafkaTrigger_succeeds() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(
            pipelineId, "kafka-trigger", "kafka", Map.of("topic", "orders"));

    mockMvc
        .perform(
            post("/api/v1/triggers")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("kafka-trigger"))
        .andExpect(jsonPath("$.triggerType").value("kafka"))
        .andExpect(jsonPath("$.webhookUrl").doesNotExist())
        .andExpect(jsonPath("$.webhookSecret").doesNotExist());
  }

  @Test
  void listTriggers_returnsTriggers() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    createTrigger(pipelineId, "trigger-1", "webhook");
    createTrigger(pipelineId, "trigger-2", "webhook");

    mockMvc
        .perform(
            get("/api/v1/triggers")
                .param("pipelineId", pipelineId.toString())
                .header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void getTrigger_returnsTrigger() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "my-trigger", "webhook");

    mockMvc
        .perform(
            get("/api/v1/triggers/{triggerId}", triggerId).header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(triggerId))
        .andExpect(jsonPath("$.name").value("my-trigger"));
  }

  @Test
  void disableTrigger_setsEnabledFalse() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "to-disable", "webhook");

    mockMvc
        .perform(
            post("/api/v1/triggers/{triggerId}/disable", triggerId)
                .header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void enableTrigger_setsEnabledTrue() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "to-enable", "webhook");

    mockMvc.perform(
        post("/api/v1/triggers/{triggerId}/disable", triggerId)
            .header("Authorization", "Bearer " + jwt));

    mockMvc
        .perform(
            post("/api/v1/triggers/{triggerId}/enable", triggerId)
                .header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void updateTrigger_updatesNameAndConfig() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "old-name", "webhook");

    UpdatePipelineTriggerRequest update =
        new UpdatePipelineTriggerRequest("new-name", Map.of("rateLimitPerMinute", 30), null);

    mockMvc
        .perform(
            put("/api/v1/triggers/{triggerId}", triggerId)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("new-name"))
        .andExpect(jsonPath("$.config.rateLimitPerMinute").value(30));
  }

  @Test
  void deleteTrigger_removesTrigger() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "to-delete", "webhook");

    mockMvc
        .perform(
            delete("/api/v1/triggers/{triggerId}", triggerId)
                .header("Authorization", "Bearer " + jwt))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/triggers/{triggerId}", triggerId).header("Authorization", "Bearer " + jwt))
        .andExpect(status().isNotFound());
  }

  @Test
  void history_returnsEmptyListForNewTrigger() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    String triggerId = createTrigger(pipelineId, "history-hook", "webhook");

    mockMvc
        .perform(
            get("/api/v1/triggers/{triggerId}/history", triggerId)
                .header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private String createTrigger(UUID pipelineId, String name, String type) throws Exception {
    Map<String, Object> config = type.equals("kafka") ? Map.of("topic", "test") : Map.of();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, name, type, config);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/triggers")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
