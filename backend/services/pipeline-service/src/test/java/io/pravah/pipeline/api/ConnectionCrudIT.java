package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.pipeline.api.dto.CreateConnectionRequest;
import io.pravah.pipeline.api.dto.UpdateConnectionRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineRequest;
import io.pravah.pipeline.infrastructure.persistence.repository.ConnectionRepository;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.util.Map;
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
class ConnectionCrudIT extends AbstractPipelinePostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer testJwtIssuer;
  @Autowired private ConnectionRepository connectionRepository;

  @Test
  void createListGetUpdateDelete_roundTrip() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateConnectionRequest create =
        new CreateConnectionRequest(
            "warehouse",
            "postgres",
            Map.of(
                "host", "localhost",
                "port", 5432,
                "database", "pravah",
                "username", "pravah",
                "credentials", Map.of("password", "env:PRAVAH_DB_PASSWORD")));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/connections")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("warehouse"))
            .andExpect(jsonPath("$.type").value("postgres"))
            .andReturn();

    UUID connectionId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(get("/api/v1/connections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("warehouse"));

    mockMvc
        .perform(
            get("/api/v1/connections/{id}", connectionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config.credentials.password").value("env:PRAVAH_DB_PASSWORD"));

    UpdateConnectionRequest update =
        new UpdateConnectionRequest(
            Map.of(
                "url", "jdbc:postgresql://localhost:5432/other",
                "username", "pravah",
                "credentials", Map.of("password", "env:PRAVAH_DB_PASSWORD")));

    mockMvc
        .perform(
            put("/api/v1/connections/{id}", connectionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config.url").value("jdbc:postgresql://localhost:5432/other"));

    mockMvc
        .perform(
            delete("/api/v1/connections/{id}", connectionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    try {
      TenantContext.setCurrentTenantId(tenantId);
      assertThat(connectionRepository.findById(connectionId)).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void validatePipeline_unknownConnectionReference_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    ValidatePipelineRequest validate =
        new ValidatePipelineRequest(
            """
            stages:
              - id: q
                type: sql
                config:
                  query: SELECT 1
                  connection: missing
            """);

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validate)))
        .andExpect(status().isBadRequest());
  }
}
