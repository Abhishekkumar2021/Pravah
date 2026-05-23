package io.pravah.scheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.api.dto.CreateScheduleRequest;
import io.pravah.scheduler.api.dto.CronPreviewRequest;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.spring.multitenancy.TenantContext;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class ScheduleApiIT extends AbstractSchedulerPostgresIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private ScheduleRepository scheduleRepository;

  @Test
  void createSchedule_persistsJsonbParameters() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateScheduleRequest body =
        new CreateScheduleRequest(pipelineId, "Daily ETL", "0 9 * * *", "UTC", null);

    mockMvc
        .perform(
            post("/api/v1/schedules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Daily ETL"))
        .andExpect(jsonPath("$.active").value(true));

    TenantContext.setCurrentTenantId(tenantId);
    try {
      assertThat(scheduleRepository.findAll()).hasSize(1);
      assertThat(scheduleRepository.findAll().getFirst().getParameters()).isEqualTo("{}");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void previewCron_returnsDescriptionAndRuns() throws Exception {
    String token = testJwtIssuer.generateAccessToken(UUID.randomUUID(), UUID.randomUUID());
    CronPreviewRequest body = new CronPreviewRequest("0 9 * * *", "UTC", null, 3);

    mockMvc
        .perform(
            post("/api/v1/schedules/preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.nextRuns.length()").value(3));
  }

  @Test
  void lifecycle_listPauseResumeDelete() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    UUID scheduleId = createSchedule(token, pipelineId, "Lifecycle");

    mockMvc
        .perform(
            get("/api/v1/schedules")
                .param("pipelineId", pipelineId.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(scheduleId.toString()));

    mockMvc
        .perform(
            get("/api/v1/schedules/{id}", scheduleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(
            post("/api/v1/schedules/{id}/pause", scheduleId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            post("/api/v1/schedules/{id}/pause", scheduleId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/schedules/{id}/resume", scheduleId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(
            delete("/api/v1/schedules/{id}", scheduleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    TenantContext.setCurrentTenantId(tenantId);
    try {
      assertThat(scheduleRepository.findAll()).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void getSchedule_returns404ForOtherTenant() throws Exception {
    UUID tenantId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(UUID.randomUUID(), tenantId);
    UUID scheduleId = createSchedule(token, UUID.randomUUID(), "Private");

    String otherToken = testJwtIssuer.generateAccessToken(UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            get("/api/v1/schedules/{id}", scheduleId)
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
  }

  private UUID createSchedule(String token, UUID pipelineId, String name) throws Exception {
    CreateScheduleRequest body =
        new CreateScheduleRequest(pipelineId, name, "0 9 * * *", "UTC", null);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/schedules")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(json.get("id").asText());
  }
}
