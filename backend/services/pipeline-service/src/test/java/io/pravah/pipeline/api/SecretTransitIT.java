package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.vault.VaultTransitKeyNames;
import io.pravah.pipeline.api.dto.CreateSecretRequest;
import io.pravah.pipeline.api.dto.UpdateSecretRequest;
import io.pravah.pipeline.application.ResolvedSecretValue;
import io.pravah.pipeline.application.SecretApplicationService;
import io.pravah.pipeline.infrastructure.persistence.repository.TenantSecretRepository;
import io.pravah.spring.multitenancy.TenantContext;
import io.pravah.test.containers.VaultContainerExtension;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests for Vault Transit tenant secret storage (ADR-007, pathway #8). */
@ExtendWith(VaultContainerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class SecretTransitIT extends AbstractPipelinePostgresIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestJwtIssuer testJwtIssuer;
  @Autowired private SecretApplicationService secretApplicationService;
  @Autowired private TenantSecretRepository secretRepository;

  @DynamicPropertySource
  static void registerVault(DynamicPropertyRegistry registry) {
    registry.add("pravah.vault.enabled", () -> "true");
    registry.add("pravah.vault.address", VaultContainerExtension::getAddress);
    registry.add("pravah.vault.auth.token", VaultContainerExtension::getRootToken);
  }

  @Test
  void createTransitSecret_resolvesAtExecution_neverReturnsValue() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);
    String expectedKey = VaultTransitKeyNames.tenantKey(tenantId);

    CreateSecretRequest createRequest =
        new CreateSecretRequest("api_token", "Stored token", null, null, "super-secret-value");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/secrets")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("api_token"))
            .andExpect(jsonPath("$.provider").value("transit"))
            .andExpect(jsonPath("$.providerPath").value(expectedKey))
            .andReturn();

    String responseBody = created.getResponse().getContentAsString();
    assertThat(responseBody).doesNotContain("super-secret-value");

    UUID secretId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(userId);
      var entity = secretRepository.findById(secretId).orElseThrow();
      assertThat(entity.getEncryptedValue()).isNotBlank();
      assertThat(entity.getEncryptedValue()).doesNotContain("super-secret-value");

      UUID executionId = UUID.randomUUID();
      ResolvedSecretValue resolved =
          secretApplicationService.resolveSecretValueForExecution(
              tenantId, executionId, Instant.now(), "api_token");
      assertThat(resolved.value()).isEqualTo("super-secret-value");
    } finally {
      TenantContext.clear();
    }

    UpdateSecretRequest updateRequest =
        new UpdateSecretRequest(null, null, null, "rotated-secret-value");

    mockMvc
        .perform(
            put("/api/v1/secrets/{id}", secretId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("transit"));

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(userId);
      ResolvedSecretValue rotated =
          secretApplicationService.resolveSecretValueForExecution(
              tenantId, UUID.randomUUID(), Instant.now(), "api_token");
      assertThat(rotated.value()).isEqualTo("rotated-secret-value");
    } finally {
      TenantContext.clear();
    }
  }
}
