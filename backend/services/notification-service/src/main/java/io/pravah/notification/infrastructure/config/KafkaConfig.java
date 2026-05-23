package io.pravah.notification.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id:notification-service}")
  private String groupId;

  @Value("${pravah.kafka.notification.dlt-topic:pravah.notification.events.dlt}")
  private String dltTopic;

  @Bean
  public ConsumerFactory<String, Map<String, Object>> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "java.util,java.lang,io.pravah");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.LinkedHashMap");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ProducerFactory<String, Map<String, Object>> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Map<String, Object>> kafkaTemplate(
      ProducerFactory<String, Map<String, Object>> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public DefaultErrorHandler notificationKafkaErrorHandler(
      KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) ->
                new org.apache.kafka.common.TopicPartition(dltTopic, record.partition()));
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.setAckAfterHandle(false);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>>
      kafkaListenerContainerFactory(DefaultErrorHandler notificationKafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setConcurrency(2);
    factory.setCommonErrorHandler(notificationKafkaErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
