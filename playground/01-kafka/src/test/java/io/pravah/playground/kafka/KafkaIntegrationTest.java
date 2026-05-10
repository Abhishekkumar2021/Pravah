package io.pravah.playground.kafka;

import io.pravah.playground.kafka.consumer.AuditConsumer;
import io.pravah.playground.kafka.producer.JobEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests using Spring's embedded Kafka.
 * No Docker required for tests — @EmbeddedKafka spins up an in-process broker.
 *
 * These tests verify the observable outcomes of each task:
 *   - Did the message arrive in the topic?
 *   - Did both consumer groups receive it?
 *   - Did the DLT receive the poison pill?
 *
 * Add the Awaitility dependency to build.gradle.kts:
 *   testImplementation("org.awaitility:awaitility:4.2.1")
 *
 * Awaitility is a polling library: await().atMost(10, SECONDS).until(() -> condition)
 * It's the standard way to test async Kafka consumers — you can't use Thread.sleep
 * because you don't know exactly when the consumer will process the message.
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
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1"
    }
)
class KafkaIntegrationTest {

    @Autowired
    private JobEventProducer producer;

    @Autowired
    private AuditConsumer auditConsumer;

    // ─────────────────────────────────────────────────────────────
    // Task 1 — Basic publish
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Call producer.publish("acme", "orders-etl", "extract")
     *   2. Assert the returned jobId is not null
     *   3. Use Awaitility to wait until auditConsumer.getAuditLog() has 1 entry
     *        await().atMost(10, TimeUnit.SECONDS)
     *               .until(() -> auditConsumer.getAuditLog().size() == 1);
     *   4. Assert the audit log entry contains "acme"
     *
     * WHY Awaitility: the consumer runs on a background thread. If you assert
     * immediately after publish, the consumer hasn't processed the message yet.
     * Awaitility polls until the condition is true or the timeout expires.
     */
    @Test
    void task1_publishCreatesMessageInTopic() {
        // TODO: implement
    }

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Two consumer groups
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Publish 3 events for different tenants: acme, beta, gamma
     *   2. Wait until auditConsumer.getAuditLog().size() >= 3
     *   3. Assert the audit log contains entries for all 3 tenants
     *
     * This proves both consumer groups received all 3 messages.
     * (The execution-service consumer processing is harder to assert in tests —
     * check the logs manually when running against the real Docker Compose setup)
     */
    @Test
    void task2_bothConsumerGroupsReceiveAllMessages() {
        // TODO: implement
    }

    // ─────────────────────────────────────────────────────────────
    // Task 4 — Poison pill → DLT
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Publish a job with stepId = "poison"
     *   2. The consumer should retry 3 times then route to DLT
     *   3. To verify the DLT received it, you need to add a counter to DeadLetterHandler.
     *      Add: private final AtomicInteger dltCount = new AtomicInteger(0);
     *           Increment it in handleDlt().
     *      Expose it via getDltCount().
     *   4. Wait until DeadLetterHandler.getDltCount() == 1
     *   5. Assert the audit log did NOT receive the poison pill
     *      (the main consumer failed — audit consumer may or may not have received it
     *       depending on whether they share partition assignment. Discuss this.)
     *
     * NOTE: The retries with exponential backoff will make this test slow (1s + 2s + 4s = 7s).
     * Set atMost(20, TimeUnit.SECONDS).
     */
    @Test
    void task4_poisonPillRoutedToDlt() {
        // TODO: implement
    }
}
