package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineRequest;
import io.pravah.pipeline.infrastructure.persistence.repository.JpaPipelineRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineEventRepository;
import io.pravah.pipeline.infrastructure.security.JwtTokenProvider;
import io.pravah.spring.multitenancy.TenantContext;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "pravah.outbox.relay.enabled=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    })
class CreatePipelineIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("pravah_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Autowired private JpaPipelineRepository pipelineRepository;

  @Autowired private PipelineEventRepository pipelineEventRepository;

  @Autowired private OutboxRepository outboxRepository;

  @BeforeEach
  void cleanDatabase() throws IOException, InterruptedException {
    // RLS hides all rows when tenant context is unset, so JPA deleteAll() would not clean anything.
    // Truncate as the container superuser (POSTGRES_USER) so each test starts from an empty schema.
    var result =
        POSTGRES.execInContainer(
            "env",
            "PGPASSWORD=" + POSTGRES.getPassword(),
            "psql",
            "-U",
            POSTGRES.getUsername(),
            "-d",
            POSTGRES.getDatabaseName(),
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            "TRUNCATE TABLE outbox, pipeline_versions, pipeline_events, pipelines CASCADE;");
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(
          "Failed to truncate pipeline tables: stdout="
              + result.getStdout()
              + " stderr="
              + result.getStderr());
    }
  }

  @Test
  void createPipelinePersistsEventAndOutbox() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body =
        new CreatePipelineRequest(
            projectId, "daily-etl", "desc", "stages:\n  - id: extract\n    type: sql\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("daily-etl"))
        .andExpect(jsonPath("$.status").value("draft"));

    TenantContext.setCurrentTenantId(tenantId);
    try {
      assertThat(pipelineRepository.findAll()).hasSize(1);
      assertThat(pipelineEventRepository.findAll()).hasSize(1);
    } finally {
      TenantContext.clear();
    }
    assertThat(outboxRepository.findAll()).hasSize(1);
  }

  @Test
  void duplicateNameInSameProjectReturns409() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body = new CreatePipelineRequest(projectId, "dup", null, "key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidYamlReturns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body =
        new CreatePipelineRequest(UUID.randomUUID(), "bad-yaml", null, "- not-a-mapping-root\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingAuthorizationHeaderReturns401() throws Exception {
    CreatePipelineRequest body =
        new CreatePipelineRequest(UUID.randomUUID(), "test", null, "key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidBearerTokenReturns401() throws Exception {
    CreatePipelineRequest body =
        new CreatePipelineRequest(UUID.randomUUID(), "test", null, "key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingProjectIdReturns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    String bodyJson = "{\"name\": \"test\", \"definitionYaml\": \"key: value\\n\"}";

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void blankNameReturns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body =
        new CreatePipelineRequest(UUID.randomUUID(), "   ", null, "key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void blankDefinitionYamlReturns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body = new CreatePipelineRequest(UUID.randomUUID(), "test", null, "   ");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyYamlReturns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body =
        new CreatePipelineRequest(UUID.randomUUID(), "empty-yaml", null, "");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tenantIsolation_cannotSeePipelinesFromOtherTenant() throws Exception {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();

    String tokenA = jwtTokenProvider.generateAccessToken(userA, tenantA, "a@example.com", "User A");
    String tokenB = jwtTokenProvider.generateAccessToken(userB, tenantB, "b@example.com", "User B");

    CreatePipelineRequest bodyA =
        new CreatePipelineRequest(projectId, "tenant-a-pipeline", null, "key: a\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyA)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/v1/pipelines")
                .param("projectId", projectId.toString())
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void sameNameDifferentProject_allowsDuplicates() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectA = UUID.randomUUID();
    UUID projectB = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest bodyA =
        new CreatePipelineRequest(projectA, "same-name", null, "key: a\n");
    CreatePipelineRequest bodyB =
        new CreatePipelineRequest(projectB, "same-name", null, "key: b\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyA)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyB)))
        .andExpect(status().isCreated());
  }

  @Test
  void responseContainsAllExpectedFields() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    CreatePipelineRequest body =
        new CreatePipelineRequest(projectId, "full-response", "test desc", "key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.name").value("full-response"))
        .andExpect(jsonPath("$.description").value("test desc"))
        .andExpect(jsonPath("$.currentVersion").value(0))
        .andExpect(jsonPath("$.status").value("draft"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void validateDefinition_validYaml_returns200() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    ValidatePipelineRequest body =
        new ValidatePipelineRequest("stages:\n  - id: extract\n    type: sql\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
  }

  @Test
  void validateDefinition_invalidYaml_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    ValidatePipelineRequest body = new ValidatePipelineRequest(":\ninvalid");

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateDefinition_notMappingRoot_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    ValidatePipelineRequest body = new ValidatePipelineRequest("- a\n- b\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateDefinition_missingAuth_returns401() throws Exception {
    ValidatePipelineRequest body = new ValidatePipelineRequest("key: value\n");

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validateDefinition_blankYaml_returns400() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "u@example.com", "User");

    ValidatePipelineRequest body = new ValidatePipelineRequest("");

    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }
}
