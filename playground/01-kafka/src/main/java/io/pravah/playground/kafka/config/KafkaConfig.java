package io.pravah.playground.kafka.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.pravah.playground.kafka.avro.JobCreated;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
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

    @Value("${pravah.kafka.schema-registry-url}")
    private String schemaRegistryUrl;

    @Value("${pravah.kafka.consumer-groups.execution-service}")
    private String executionServiceGroupId;

    @Value("${pravah.kafka.consumer-groups.audit-service}")
    private String auditServiceGroupId;

    @Value("${pravah.kafka.topics.job-created-dlt}")
    private String dltTopic;

    // ─────────────────────────────────────────────────────────────
    // Consumer — execution-service group
    // ─────────────────────────────────────────────────────────────

    /**
     * Listener container factory for the execution-service consumer group.
     *
     * A "factory" here is not a factory in the design-pattern sense — it is the
     * configuration object that Spring Kafka uses to spin up listener threads.
     * When you annotate a method with @KafkaListener(containerFactory = "executionServiceFactory"),
     * Spring looks up this bean by name and uses it to start the consumer.
     *
     * Why does this method accept KafkaTemplate?
     * The DLT recoverer needs a template to PUBLISH the failed message to the DLT topic.
     * Spring injects the auto-configured KafkaTemplate here.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobCreated> executionServiceFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {

        // Step 1: build a ConsumerFactory with the execution-service group ID
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, executionServiceGroupId);
        ConsumerFactory<String, JobCreated> consumerFactory = new DefaultKafkaConsumerFactory<>(props);

        // Step 2: create the listener container factory
        ConcurrentKafkaListenerContainerFactory<String, JobCreated> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // MANUAL_IMMEDIATE: we call acknowledgment.acknowledge() ourselves — Kafka does not
        // auto-commit offsets. This is the safest mode: the message is only marked "done"
        // after your processing code succeeds.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 3 concurrent threads = one per partition (we created 3 partitions in docker-compose).
        // In production this is tuned to match the partition count.
        factory.setConcurrency(3);

        // Step 3: DLT error handler
        // When a message fails, Spring retries with exponential backoff.
        // After maxAttempts is exhausted, DeadLetterPublishingRecoverer publishes the message
        // to the DLT topic, preserving all original headers plus adding DLT-specific headers.
        //
        // (record, ex) -> new TopicPartition(dltTopic, record.partition())
        //   → routes the failed message to the SAME partition number in the DLT as its
        //     source partition. This preserves ordering for operators replaying the DLT.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(dltTopic, record.partition())
        );
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0); // 1s, 2s, 4s
        backOff.setMaxAttempts(3);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // ─────────────────────────────────────────────────────────────
    // Consumer — audit-service group
    // ─────────────────────────────────────────────────────────────

    /**
     * Listener container factory for the audit-service consumer group.
     *
     * Same structure as executionServiceFactory but:
     *   - Different group.id  → independent offset tracking
     *   - No DLT handler      → audit is append-only; if it fails, we log and move on.
     *                           We don't want a DLT-on-DLT chain.
     *
     * This factory is also reused by DeadLetterHandler (which reads the DLT topic)
     * because the DLT consumer also doesn't need retry/DLT routing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobCreated> auditServiceFactory() {
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, auditServiceGroupId);
        ConsumerFactory<String, JobCreated> consumerFactory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentKafkaListenerContainerFactory<String, JobCreated> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);

        return factory;
    }

    // ─────────────────────────────────────────────────────────────
    // Shared helper
    // ─────────────────────────────────────────────────────────────

    /**
     * Properties shared by ALL consumer groups.
     * Extracted so neither factory duplicates this map.
     *
     * Each caller adds its own GROUP_ID_CONFIG on top of this base.
     */
    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();

        // Which Kafka broker(s) to connect to
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Keys are plain strings (tenant_id), values are Avro records
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

        // The deserializer fetches the Avro schema from this URL using the schema ID
        // embedded in each message (first 5 bytes: 0x00 + 4-byte schema ID)
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        // Without this, the deserializer returns a GenericRecord (a map-like object).
        // With this = true, it returns a strongly-typed JobCreated Java class.
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        // Start reading from offset 0 on the first run (no committed offsets yet).
        // Change to "latest" if you only want new messages.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // We manage commits manually via Acknowledgment.acknowledge() — never auto-commit.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return props;
    }
}
