package io.pravah.playground.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: creates an order → outbox row written atomically →
 * publisher polls and sends to Kafka → consumer receives event.
 *
 * Uses Testcontainers for Postgres (real DB with Flyway migrations) and
 * @EmbeddedKafka for Kafka (in-process, no Docker Kafka needed).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(
        partitions = 1,
        topics = {"orders.events"},
        brokerProperties = {
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        }
)
class OutboxPatternIT {

    @SuppressWarnings("resource") // Testcontainers @Container handles lifecycle
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("outbox_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("outbox.poll-interval-ms", () -> "50");
    }

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "${spring.embedded.kafka.brokers}");
    }

    @Autowired
    OrderService orderService;

    @Autowired
    OutboxEventRepository outboxRepo;

    /**
     * Creates an order — the outbox row is written in the same transaction.
     * Waits for the scheduled publisher to push the event to Kafka, then verifies
     * the consumer receives it.
     */
    @Test
    void atomicOutboxPublish() {
        Order order = orderService.createOrder("Widget", 5);

        try (Consumer<String, String> consumer = buildConsumer()) {
            consumer.subscribe(Collections.singletonList("orders.events"));

            List<String> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                records.forEach(r -> received.add(r.value()));
                assertThat(received)
                        .anyMatch(payload -> payload.contains(order.getId().toString()));
            });
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var event = outboxRepo.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(order.getId().toString()))
                    .findFirst()
                    .orElseThrow();
            assertThat(event.getPublishedAt()).as("published_at should be set").isNotNull();
        });
    }

    private Consumer<String, String> buildConsumer() {
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                        ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
    }
}
