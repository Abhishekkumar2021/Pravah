package io.pravah.execution.infrastructure.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Kafka consumer configuration for execution-service (US-02.09 parallel execution).
 *
 * <p>The job-worker listener supports configurable concurrency via {@code
 * pravah.kafka.job-worker.concurrency} (default 4). Each concurrent consumer can process a job in
 * parallel, enabling true parallel stage execution across multiple Kafka partitions.
 */
@Configuration
@EnableKafka
public class ExecutionKafkaConsumerConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ExecutionKafkaConsumerConfiguration.class);

  @Bean
  @ConditionalOnProperty(
      name = "pravah.kafka.execution-created-listener-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ConsumerFactory<String, Map<String, Object>> executionEventsKafkaConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.consumer.group-id}") String groupId) {
    return jsonMapConsumerFactory(bootstrapServers, groupId);
  }

  @Bean
  @ConditionalOnProperty(
      name = "pravah.kafka.execution-created-listener-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>>
      executionKafkaListenerContainerFactory(
          ConsumerFactory<String, Map<String, Object>> executionEventsKafkaConsumerFactory) {
    return manualAckFactory(executionEventsKafkaConsumerFactory);
  }

  @Bean
  @ConditionalOnProperty(name = "pravah.kafka.job-worker-listener-enabled", havingValue = "true")
  public ConsumerFactory<String, Map<String, Object>> jobWorkerKafkaConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${pravah.kafka.job-worker.consumer-group-id}") String groupId) {
    return jsonMapConsumerFactory(bootstrapServers, groupId);
  }

  @Bean
  @ConditionalOnProperty(name = "pravah.kafka.job-worker-listener-enabled", havingValue = "true")
  public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>>
      jobWorkerKafkaListenerContainerFactory(
          ConsumerFactory<String, Map<String, Object>> jobWorkerKafkaConsumerFactory,
          @Value("${pravah.kafka.job-worker.concurrency:4}") int concurrency) {
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
        manualAckFactory(jobWorkerKafkaConsumerFactory);
    factory.setConcurrency(concurrency);
    log.info("Job worker Kafka listener configured with concurrency={}", concurrency);
    return factory;
  }

  @SuppressWarnings("unchecked")
  private static ConsumerFactory<String, Map<String, Object>> jsonMapConsumerFactory(
      String bootstrapServers, String groupId) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<Map<String, Object>> jsonDeserializer =
        new JsonDeserializer<>((Class<Map<String, Object>>) (Class<?>) Map.class, false);
    jsonDeserializer.addTrustedPackages("java.util", "java.lang", "io.pravah");

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jsonDeserializer);
  }

  private static ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>>
      manualAckFactory(ConsumerFactory<String, Map<String, Object>> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
  }
}
