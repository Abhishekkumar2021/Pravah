package io.pravah.execution.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import io.pravah.common.domain.JobState;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end: outbox relay publishes {@code execution.created}, consumer queues jobs (LLD §2),
 * embedded job worker consumes {@code job.created} and completes stages (LLD §3 MVP).
 *
 * <p>Uses embedded Kafka + shared Postgres container. Publishes the outbox payload with {@link
 * KafkaTemplate} (same wire format as {@link
 * io.pravah.execution.infrastructure.outbox.OutboxRelay}) so the test does not depend on the relay
 * bean lifecycle.
 */
@ExtendWith(PostgresContainerExtension.class)
@EmbeddedKafka(
    partitions = 1,
    topics = {"pravah.execution.execution.events", "pravah.job.created"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
      "pravah.realtime.redis-enabled=false",
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.consumer.group-id=execution-service-kafka-it",
      "pravah.kafka.job-worker.consumer-group-id=execution-service-kafka-it-jobs",
      "pravah.outbox.relay.enabled=false",
      "pravah.kafka.execution-created-listener-enabled=true",
      "pravah.kafka.job-worker-listener-enabled=true"
    })
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionKafkaDispatchIT {

  private static final String EXECUTION_EVENTS_TOPIC = "pravah.execution.execution.events";
  private static final String JOB_CREATED_TOPIC = "pravah.job.created";

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private PipelineCatalog pipelineCatalog;

  @Autowired private JobEntityRepository jobEntityRepository;

  @Autowired private OutboxRepository outboxRepository;

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  private final Set<UUID> publishedJobCreatedEventIds = new HashSet<>();

  @BeforeEach
  void resetMocksAndData() throws Exception {
    publishedJobCreatedEventIds.clear();
    reset(pipelineCatalog);
    try (var c =
            java.sql.DriverManager.getConnection(
                PostgresContainerExtension.getJdbcUrl(),
                PostgresContainerExtension.getUsername(),
                PostgresContainerExtension.getPassword());
        var s = c.createStatement()) {
      s.execute(
          "TRUNCATE TABLE processed_events; TRUNCATE TABLE outbox; TRUNCATE TABLE executions CASCADE;");
    }
  }

  @Test
  void manualRun_embeddedWorker_completesParallelRootStages() throws Exception {
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

    dispatchExecutionCreatedFromOutbox();

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs).hasSize(2);
              assertThat(jobs).allMatch(j -> j.getStatus() == JobState.QUEUED);
            });

    drainNewJobCreatedMessagesToKafka();

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs).allMatch(j -> j.getStatus() == JobState.SUCCEEDED);
            });

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));
  }

  @Test
  void manualRun_embeddedWorker_runsStagesWithDependsOnInOrder() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract data"),
                Map.of("id", "load", "name", "Load", "dependsOn", List.of("extract"))));

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

    dispatchExecutionCreatedFromOutbox();

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs).hasSize(2);
              assertThat(jobs.getFirst().getStageId()).isEqualTo("extract");
              assertThat(jobs.getFirst().getStatus()).isEqualTo(JobState.QUEUED);
              assertThat(jobs.get(1).getStageId()).isEqualTo("load");
              assertThat(jobs.get(1).getStatus()).isEqualTo(JobState.PENDING);
            });

    drainNewJobCreatedMessagesToKafka();

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs.getFirst().getStatus()).isEqualTo(JobState.SUCCEEDED);
              assertThat(jobs.get(1).getStatus()).isEqualTo(JobState.QUEUED);
            });

    drainNewJobCreatedMessagesToKafka();

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs).allMatch(j -> j.getStatus() == JobState.SUCCEEDED);
            });

    mockMvc
        .perform(
            get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));
  }

  private void dispatchExecutionCreatedFromOutbox() throws Exception {
    OutboxEntity row =
        outboxRepository.findAll().stream()
            .filter(o -> ExecutionEventTypes.EXECUTION_CREATED.equals(o.getEventType()))
            .findFirst()
            .orElseThrow();
    kafkaTemplate
        .send(EXECUTION_EVENTS_TOPIC, row.getPartitionKey(), row.getPayload())
        .get(15, SECONDS);
  }

  /**
   * Publishes every {@code job.created} outbox row whose {@code eventId} has not been published in
   * this test method yet. Call again after the worker enqueues downstream jobs.
   */
  private void drainNewJobCreatedMessagesToKafka() throws Exception {
    List<OutboxEntity> pending =
        outboxRepository.findAll().stream()
            .filter(o -> JobEventTypes.JOB_CREATED.equals(o.getEventType()))
            .filter(
                o ->
                    !publishedJobCreatedEventIds.contains(
                        UUID.fromString(o.getPayload().get("eventId").toString())))
            .toList();
    for (OutboxEntity o : pending) {
      UUID eventId = UUID.fromString(o.getPayload().get("eventId").toString());
      publishedJobCreatedEventIds.add(eventId);
      kafkaTemplate.send(JOB_CREATED_TOPIC, o.getPartitionKey(), o.getPayload()).get(15, SECONDS);
    }
  }
}
