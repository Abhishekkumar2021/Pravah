package io.pravah.playground.kafka.config;

import io.pravah.playground.kafka.avro.JobCreated;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Central Kafka configuration.
 *
 * Spring Boot auto-configures a basic KafkaTemplate from application.yml,
 * but we need custom configuration here for:
 *   1. Separate listener container factories per consumer group
 *      (execution-service vs audit-service have different group IDs)
 *   2. Dead Letter Topic (DLT) error handler with exponential backoff
 *   3. Transactional producer for Task 3
 *
 * Why separate factories per consumer group?
 * Each @KafkaListener needs its own group.id. Spring's auto-configured
 * factory uses a single group ID. We create named factories so each listener
 * can declare which factory (= which group) it uses.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${pravah.kafka.consumer-groups.execution-service}")
    private String executionServiceGroupId;

    @Value("${pravah.kafka.consumer-groups.audit-service}")
    private String auditServiceGroupId;

    @Value("${pravah.kafka.topics.job-created-dlt}")
    private String dltTopic;

    // ─────────────────────────────────────────────────────────────
    // Producer
    // ─────────────────────────────────────────────────────────────

    /**
     * The KafkaTemplate is the primary way to publish messages.
     * Spring Boot auto-creates one from application.yml — we reuse it here.
     * The transactional producer (Task 3) uses the same template but wraps
     * sends in kafkaTemplate.executeInTransaction(...).
     *
     * Spring Boot auto-configures this bean. Nothing to add here unless
     * you need a custom ProducerFactory.
     */

    // ─────────────────────────────────────────────────────────────
    // Consumer — execution-service group
    // ─────────────────────────────────────────────────────────────

    /**
     * Listener container factory for the execution-service consumer group.
     *
     * TODO (Task 1-4):
     *   1. Create a ConsumerFactory<String, JobCreated> with these properties:
     *        - bootstrap.servers
     *        - group.id = executionServiceGroupId
     *        - key.deserializer = StringDeserializer
     *        - value.deserializer = KafkaAvroDeserializer (Confluent)
     *        - schema.registry.url
     *        - specific.avro.reader = true  ← returns JobCreated, not GenericRecord
     *        - auto.offset.reset = earliest
     *        - enable.auto.commit = false
     *
     *   2. Create a ConcurrentKafkaListenerContainerFactory using that ConsumerFactory.
     *        - setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE)
     *        - setConcurrency(3)   ← one thread per partition
     *
     *   3. Attach the DefaultErrorHandler for DLT routing (Task 4):
     *        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
     *            kafkaTemplate,
     *            (record, ex) -> new TopicPartition(dltTopic, record.partition())
     *        );
     *        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
     *        backOff.setMaxAttempts(3);
     *        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
     *        factory.setCommonErrorHandler(errorHandler);
     *
     * Bean name "executionServiceFactory" is referenced in @KafkaListener(containerFactory = "executionServiceFactory")
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobCreated> executionServiceFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {

        // TODO: implement as described above
        throw new UnsupportedOperationException("TODO: implement executionServiceFactory");
    }

    // ─────────────────────────────────────────────────────────────
    // Consumer — audit-service group
    // ─────────────────────────────────────────────────────────────

    /**
     * Listener container factory for the audit-service consumer group.
     *
     * TODO (Task 2):
     *   Same structure as executionServiceFactory but with:
     *     - group.id = auditServiceGroupId
     *     - No DLT error handler needed for audit (it's append-only, failures are non-critical)
     *     - setConcurrency(3)
     *
     * Bean name "auditServiceFactory" referenced in @KafkaListener(containerFactory = "auditServiceFactory")
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobCreated> auditServiceFactory() {

        // TODO: implement as described above
        throw new UnsupportedOperationException("TODO: implement auditServiceFactory");
    }

    // ─────────────────────────────────────────────────────────────
    // Shared helper
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds the base consumer properties shared by both factories.
     * Extract this to avoid duplicating the property map in both factory methods.
     *
     * TODO: implement this helper — returns a Map<String, Object> with:
     *   ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG → bootstrapServers
     *   ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG → StringDeserializer.class
     *   ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG → KafkaAvroDeserializer.class
     *   "schema.registry.url" → schemaRegistryUrl
     *   "specific.avro.reader" → true
     *   ConsumerConfig.AUTO_OFFSET_RESET_CONFIG → "earliest"
     *   ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG → false
     */
    private Map<String, Object> baseConsumerProps() {
        // TODO: implement
        return new HashMap<>();
    }
}
