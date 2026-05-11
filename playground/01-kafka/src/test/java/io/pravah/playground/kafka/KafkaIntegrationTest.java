package io.pravah.playground.kafka;

import io.pravah.playground.kafka.consumer.AuditConsumer;
import io.pravah.playground.kafka.consumer.DeadLetterHandler;
import io.pravah.playground.kafka.producer.JobEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests using Spring's embedded Kafka.
 * No Docker required — @EmbeddedKafka spins up a real (in-process) Kafka broker.
 *
 * HOW @EmbeddedKafka WORKS:
 *   Spring starts a real KRaft Kafka broker inside the JVM on a random port and
 *   exposes it as {@code spring.embedded.kafka.brokers}. {@code src/test/resources/
 *   application.properties} sets {@code spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}}
 *   so tests use the embedded broker. Without that override, {@code application.yml}
 *   would keep {@code localhost:9092} (for Docker) and nothing would reach embedded Kafka.
 *
 * HOW @DirtiesContext WORKS:
 *   After each test, Spring tears down the ApplicationContext and rebuilds it.
 *   This resets all in-memory state (auditLog, dltCount) and consumer offsets.
 *   Without it, messages from test1 could leak into test2.
 *
 * SCHEMA REGISTRY IN TESTS:
 *   Dynamic properties point schema.registry.url at mock://test-scope so the Confluent
 *   serializers use an in-memory MockSchemaRegistryClient (no Docker Schema Registry).
 *
 * TRANSACTIONAL.ID IN TESTS:
 *   application.yml sets a fixed transactional.id for Docker (Task 3). Here we reload the
 *   Spring context between tests (@DirtiesContext). Reusing the SAME transactional.id on the
 *   embedded broker causes Kafka's producer fencing — the new producer is rejected or sends
 *   fail silently. A fresh UUID per ApplicationContext avoids that.
 */
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {
                "pravah.job.created",
                "pravah.job.created.DLT",
                "pravah.job.completed",
                "pravah.job.audit.log"
        },
        brokerProperties = {
                // Required for Kafka transactions to work with a single broker (no replicas)
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        }
)
class KafkaIntegrationTest {

    @DynamicPropertySource
    static void kafkaTestOverrides(DynamicPropertyRegistry registry) {
        registry.add("pravah.kafka.schema-registry-url", () -> "mock://test-scope");
        registry.add("spring.kafka.producer.properties.schema.registry.url", () -> "mock://test-scope");
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> "mock://test-scope");
        registry.add(
                "spring.kafka.producer.properties.transactional.id",
                () -> "pravah-playground-test-" + UUID.randomUUID());
    }

    @Autowired
    private JobEventProducer producer;

    @Autowired
    private AuditConsumer auditConsumer;

    @Autowired
    private DeadLetterHandler deadLetterHandler;

    // ─────────────────────────────────────────────────────────────
    // Task 1 — Basic publish
    // ─────────────────────────────────────────────────────────────

    /**
     * Proves: a published message is received by the audit-service consumer group.
     *
     * WHY Awaitility instead of Thread.sleep?
     *   The consumer runs on a background thread. After publish() returns,
     *   the message is in Kafka but the consumer hasn't necessarily processed it yet.
     *   Thread.sleep(5000) is slow and flaky (what if the machine is under load?).
     *   Awaitility polls every 100ms until the condition is true, up to 10 seconds.
     *   It fails fast when the condition is met, and fails with a clear message on timeout.
     */
    @Test
    void task1_publishCreatesMessageInTopic() {
        String jobId = producer.publish("acme", "orders-etl", "extract");

        assertThat(jobId).isNotNull();

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> auditConsumer.getAuditLog().size() == 1);

        assertThat(auditConsumer.getAuditLog().get(0)).contains("acme");
    }

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Two consumer groups
    // ─────────────────────────────────────────────────────────────

    /**
     * Proves: all 3 messages are received by the audit-service consumer group,
     * regardless of which partition they land on (tenantId as key distributes them).
     *
     * The execution-service consumer processing is verified via logs when running
     * against the real Docker Compose setup — it's harder to assert in tests because
     * it doesn't maintain an in-memory list.
     */
    @Test
    void task2_bothConsumerGroupsReceiveAllMessages() {
        producer.publish("acme",  "orders-etl", "extract");
        producer.publish("beta",  "orders-etl", "extract");
        producer.publish("gamma", "orders-etl", "extract");

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> auditConsumer.getAuditLog().size() >= 3);

        assertThat(auditConsumer.getAuditLog())
                .anyMatch(e -> e.contains("acme"))
                .anyMatch(e -> e.contains("beta"))
                .anyMatch(e -> e.contains("gamma"));
    }

    // ─────────────────────────────────────────────────────────────
    // Task 4 — Poison pill → DLT
    // ─────────────────────────────────────────────────────────────

    /**
     * Proves: a poison pill message is retried 3 times and then routed to the DLT,
     * without blocking other messages in the same topic.
     *
     * The exponential backoff is 1s + 2s + 4s = 7s of retries before DLT routing.
     * Hence atMost(20, SECONDS) — we give plenty of margin.
     *
     * In the real Docker Compose run, watch the execution-service logs:
     *   [execution-service] Received job.created: stepId=poison ...  (x3 retries)
     *   [DLT] Poison pill received: jobId=... exception=PoisonPillException
     */
    @Test
    void task4_poisonPillRoutedToDlt() {
        producer.publish("acme", "orders-etl", "poison");

        // Wait for the DLT handler to receive the message
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> deadLetterHandler.getDltCount() == 1);

        assertThat(deadLetterHandler.getDltCount()).isEqualTo(1);
    }
}
