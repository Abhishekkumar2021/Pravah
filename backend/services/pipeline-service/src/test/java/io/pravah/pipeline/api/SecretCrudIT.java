package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.pipeline.api.dto.CreateSecretRequest;
import io.pravah.pipeline.api.dto.UpdateSecretRequest;
import io.pravah.pipeline.infrastructure.persistence.repository.TenantSecretRepository;
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
class SecretCrudIT extends AbstractPipelinePostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer testJwtIssuer;
  @Autowired private TenantSecretRepository secretRepository;

  @Test
  void createListGetUpdateDelete_roundTrip() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateSecretRequest createRequest =
        new CreateSecretRequest("api_key", "External API key", "env", "MY_API_KEY", null);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/secrets")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("api_key"))
            .andExpect(jsonPath("$.provider").value("env"))
            .andExpect(jsonPath("$.providerPath").value("MY_API_KEY"))
            .andReturn();

    UUID secretId =
        UUID.fromString(
            objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(get("/api/v1/secrets").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("api_key"));

    mockMvc
        .perform(get("/api/v1/secrets/{id}", secretId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("api_key"));

    mockMvc
        .perform(
            get("/api/v1/secrets/by-name/{name}", "api_key")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(secretId.toString()));

    UpdateSecretRequest updateRequest =
        new UpdateSecretRequest("Updated description", "env", "NEW_ENV_VAR", null);

    mockMvc
        .perform(
            put("/api/v1/secrets/{id}", secretId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.providerPath").value("NEW_ENV_VAR"));

    mockMvc
        .perform(
            delete("/api/v1/secrets/{id}", secretId).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    try {
      TenantContext.setCurrentTenantId(tenantId);
      assertThat(secretRepository.findById(secretId)).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void createSecret_duplicateName_returns409() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateSecretRequest request = new CreateSecretRequest("dup_secret", null, "env", "VAR", null);

    mockMvc
        .perform(
            post("/api/v1/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void createSecret_invalidName_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateSecretRequest request = new CreateSecretRequest("Invalid-Name", null, "env", "VAR", null);

    mockMvc
        .perform(
            post("/api/v1/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSecret_invalidProvider_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateSecretRequest request =
        new CreateSecretRequest("valid_name", null, "unknown_provider", "path", null);

    mockMvc
        .perform(
            post("/api/v1/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSecret_vaultProviderWithoutKey_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreateSecretRequest request =
        new CreateSecretRequest("vault_secret", null, "vault", "secret/path/without/key", null);

    mockMvc
        .perform(
            post("/api/v1/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("key")));
  }
}
