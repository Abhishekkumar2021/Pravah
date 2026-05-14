package io.pravah.execution.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.JobState;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * End-to-end: outbox relay publishes {@code execution.created}, consumer queues jobs (LLD §2).
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
      // application.yml excludes Kafka auto-config for faster ITs; this test needs KafkaTemplate.
      "spring.autoconfigure.exclude=",
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.consumer.group-id=execution-service-kafka-it",
      "pravah.outbox.relay.enabled=false",
      "pravah.kafka.execution-created-listener-enabled=true"
    })
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionKafkaDispatchIT {

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

  private static final String EXECUTION_EVENTS_TOPIC = "pravah.execution.execution.events";

  @BeforeEach
  void resetMocksAndData() throws Exception {
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
  void manualRun_outboxRelayAndConsumer_queueRootJobs() throws Exception {
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

    var outboxRows = outboxRepository.findAll();
    assertThat(outboxRows).hasSize(1);
    var row = outboxRows.getFirst();
    kafkaTemplate
        .send(EXECUTION_EVENTS_TOPIC, row.getAggregateId().toString(), row.getPayload())
        .get(15, SECONDS);

    await()
        .atMost(Duration.ofSeconds(25))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
              assertThat(jobs).isNotEmpty();
              assertThat(jobs).allMatch(j -> j.getStatus() == JobState.QUEUED);
            });
  }
}
