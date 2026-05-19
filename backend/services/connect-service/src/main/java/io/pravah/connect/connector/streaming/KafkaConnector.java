package io.pravah.connect.connector.streaming;

import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

/**
 * Kafka connector for producing and consuming messages from Apache Kafka topics.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Reading from topics (Source)
 *   <li>Writing to topics (Sink)
 *   <li>SASL/SSL authentication
 *   <li>Consumer groups
 * </ul>
 */
@Component
public class KafkaConnector implements SourceConnector, SinkConnector {

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder()
        .id("kafka")
        .name("Apache Kafka")
        .description("Stream data from and to Apache Kafka topics")
        .icon("kafka")
        .category("Streaming")
        .type(ConnectorType.STREAMING)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .version("1.0.0")
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "streaming", true,
                "batch", true,
                "partitions", true,
                "consumer_groups", true))
        .tags(List.of("kafka", "streaming", "event", "messaging", "real-time"))
        .build();
  }

  private List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder()
            .name("bootstrap_servers")
            .label("Bootstrap Servers")
            .description("Comma-separated list of host:port pairs (e.g., localhost:9092)")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .placeholder("localhost:9092")
            .group("Connection")
            .order(1)
            .build(),
        ConfigField.builder()
            .name("security_protocol")
            .label("Security Protocol")
            .description("Protocol used to communicate with brokers")
            .type(ConfigField.FieldType.SELECT)
            .required(false)
            .defaultValue("PLAINTEXT")
            .options(List.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"))
            .group("Security")
            .order(2)
            .build(),
        ConfigField.builder()
            .name("sasl_mechanism")
            .label("SASL Mechanism")
            .description("SASL mechanism for authentication")
            .type(ConfigField.FieldType.SELECT)
            .required(false)
            .options(List.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512", "OAUTHBEARER"))
            .group("Security")
            .order(3)
            .dependsOn("security_protocol")
            .condition("SASL_PLAINTEXT,SASL_SSL")
            .build(),
        ConfigField.builder()
            .name("sasl_username")
            .label("SASL Username")
            .description("Username for SASL authentication")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Security")
            .order(4)
            .dependsOn("sasl_mechanism")
            .build(),
        ConfigField.builder()
            .name("sasl_password")
            .label("SASL Password")
            .description("Password for SASL authentication")
            .type(ConfigField.FieldType.PASSWORD)
            .required(false)
            .group("Security")
            .order(5)
            .dependsOn("sasl_mechanism")
            .build(),
        ConfigField.builder()
            .name("consumer_group")
            .label("Consumer Group ID")
            .description("Consumer group for reading messages")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .defaultValue("pravah-connect")
            .group("Consumer")
            .order(6)
            .build(),
        ConfigField.builder()
            .name("auto_offset_reset")
            .label("Auto Offset Reset")
            .description("What to do when there is no initial offset")
            .type(ConfigField.FieldType.SELECT)
            .required(false)
            .defaultValue("earliest")
            .options(List.of("earliest", "latest", "none"))
            .group("Consumer")
            .order(7)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();

    String bootstrapServers = (String) config.get("bootstrap_servers");
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      errors.put("bootstrap_servers", "Bootstrap servers are required");
    }

    String securityProtocol = (String) config.get("security_protocol");
    if (securityProtocol != null && securityProtocol.contains("SASL")) {
      if (config.get("sasl_mechanism") == null) {
        errors.put("sasl_mechanism", "SASL mechanism is required when using SASL");
      }
      if (config.get("sasl_username") == null || config.get("sasl_password") == null) {
        errors.put("sasl_username", "SASL username and password are required");
      }
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    Properties props = buildAdminProperties(config);

    try (AdminClient adminClient = AdminClient.create(props)) {
      ListTopicsResult topics = adminClient.listTopics();
      int topicCount = topics.names().get(5, TimeUnit.SECONDS).size();

      long latency = System.currentTimeMillis() - start;
      return new TestResult(
          true,
          String.format("Successfully connected. Found %d topics.", topicCount),
          latency,
          Map.of("topic_count", topicCount));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new TestResult(
          false, "Connection interrupted", System.currentTimeMillis() - start, null);
    } catch (ExecutionException e) {
      return new TestResult(
          false,
          "Connection failed: " + e.getCause().getMessage(),
          System.currentTimeMillis() - start,
          null);
    } catch (TimeoutException e) {
      return new TestResult(
          false, "Connection timed out", System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    Properties props = buildAdminProperties(config);
    List<StreamInfo> streams = new ArrayList<>();

    try (AdminClient adminClient = AdminClient.create(props)) {
      Set<String> topics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);

      for (String topic : topics) {
        if (topic.startsWith("__")) {
          continue;
        }

        List<PartitionInfo> partitions =
            adminClient
                .describeTopics(List.of(topic))
                .topicNameValues()
                .get(topic)
                .get(5, TimeUnit.SECONDS)
                .partitions()
                .stream()
                .map(
                    p ->
                        new PartitionInfo(
                            topic,
                            p.partition(),
                            null,
                            new org.apache.kafka.common.Node[0],
                            new org.apache.kafka.common.Node[0]))
                .toList();

        streams.add(
            new StreamInfo(
                topic,
                null,
                List.of(
                    new FieldInfo("key", "string", true, "Message key"),
                    new FieldInfo("value", "string", false, "Message value"),
                    new FieldInfo("timestamp", "long", true, "Message timestamp"),
                    new FieldInfo("partition", "integer", false, "Partition number"),
                    new FieldInfo("offset", "long", false, "Message offset")),
                List.of(),
                Map.of("partition_count", partitions.size())));
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to discover topics: " + e.getMessage(), e);
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String topic, ReadOptions options) {
    return new KafkaRecordIterator(config, topic, options);
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String topic, List<Record> records, WriteOptions writeOptions) {
    long start = System.currentTimeMillis();
    Properties props = buildProducerProperties(config);
    int written = 0;

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      for (Record record : records) {
        String key =
            record.data().containsKey("key") ? String.valueOf(record.data().get("key")) : null;
        String value =
            record.data().containsKey("value")
                ? String.valueOf(record.data().get("value"))
                : record.data().toString();

        producer.send(new ProducerRecord<>(topic, key, value)).get(5, TimeUnit.SECONDS);
        written++;
      }

      producer.flush();
    } catch (Exception e) {
      throw new RuntimeException("Failed to write to Kafka: " + e.getMessage(), e);
    }

    return new WriteResult(written, 0, System.currentTimeMillis() - start, Map.of("topic", topic));
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // Kafka topics are schema-less by default; schema registry integration would go here
  }

  private Properties buildAdminProperties(Map<String, Object> config) {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.get("bootstrap_servers"));
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);

    addSecurityProperties(props, config);
    return props;
  }

  private Properties buildConsumerProperties(Map<String, Object> config) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get("bootstrap_servers"));
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG, config.getOrDefault("consumer_group", "pravah-connect"));
    props.put(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        config.getOrDefault("auto_offset_reset", "earliest"));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

    addSecurityProperties(props, config);
    return props;
  }

  private Properties buildProducerProperties(Map<String, Object> config) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get("bootstrap_servers"));
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

    addSecurityProperties(props, config);
    return props;
  }

  private void addSecurityProperties(Properties props, Map<String, Object> config) {
    String securityProtocol = (String) config.get("security_protocol");
    if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
      props.put("security.protocol", securityProtocol);

      if (securityProtocol.contains("SASL")) {
        String mechanism = (String) config.get("sasl_mechanism");
        props.put("sasl.mechanism", mechanism);

        String username = (String) config.get("sasl_username");
        String password = (String) config.get("sasl_password");
        String jaasConfig =
            String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password);
        props.put("sasl.jaas.config", jaasConfig);
      }
    }
  }

  private class KafkaRecordIterator implements RecordIterator {
    private final KafkaConsumer<String, String> consumer;
    private final Iterator<ConsumerRecord<String, String>> currentBatch;
    private ConsumerRecord<String, String> next;
    private long lastOffset = -1;
    private boolean closed = false;

    KafkaRecordIterator(Map<String, Object> config, String topic, ReadOptions options) {
      Properties props = buildConsumerProperties(config);
      this.consumer = new KafkaConsumer<>(props);
      this.consumer.subscribe(List.of(topic));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
      this.currentBatch = records.iterator();
      advance();
    }

    private void advance() {
      if (currentBatch.hasNext()) {
        next = currentBatch.next();
        lastOffset = next.offset();
      } else {
        next = null;
      }
    }

    @Override
    public boolean hasNext() {
      return next != null && !closed;
    }

    @Override
    public Record next() {
      if (next == null) {
        throw new NoSuchElementException();
      }
      ConsumerRecord<String, String> current = next;
      advance();

      Map<String, Object> data = new HashMap<>();
      data.put("key", current.key());
      data.put("value", current.value());
      data.put("timestamp", current.timestamp());
      data.put("partition", current.partition());
      data.put("offset", current.offset());

      return new Record(data, String.valueOf(current.offset()), current.timestamp());
    }

    @Override
    public String getCursor() {
      return String.valueOf(lastOffset);
    }

    @Override
    public void close() {
      closed = true;
      try {
        consumer.close(Duration.ofSeconds(5));
      } catch (Exception ignored) {
        // Ignore close errors
      }
    }
  }
}
