package io.pravah.connect.connector.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ connector for producing and consuming messages from RabbitMQ queues and exchanges.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Queue consumption (Source)
 *   <li>Publishing to exchanges (Sink)
 *   <li>Direct, fanout, topic, and headers exchanges
 *   <li>SSL/TLS connections
 * </ul>
 */
@Component
public class RabbitMqConnector implements SourceConnector, SinkConnector {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder()
        .id("rabbitmq")
        .name("RabbitMQ")
        .description("Stream data from and to RabbitMQ queues and exchanges")
        .icon("rabbitmq")
        .category("Streaming")
        .type(ConnectorType.STREAMING)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .version("1.0.0")
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "streaming", true,
                "exchanges", true,
                "routing_keys", true,
                "acknowledgements", true))
        .tags(List.of("rabbitmq", "amqp", "messaging", "queue", "exchange"))
        .build();
  }

  private List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder()
            .name("host")
            .label("Host")
            .description("RabbitMQ server hostname")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .placeholder("localhost")
            .group("Connection")
            .order(1)
            .build(),
        ConfigField.builder()
            .name("port")
            .label("Port")
            .description("RabbitMQ server port")
            .type(ConfigField.FieldType.NUMBER)
            .required(false)
            .defaultValue(5672)
            .group("Connection")
            .order(2)
            .build(),
        ConfigField.builder()
            .name("virtual_host")
            .label("Virtual Host")
            .description("RabbitMQ virtual host")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .defaultValue("/")
            .group("Connection")
            .order(3)
            .build(),
        ConfigField.builder()
            .name("username")
            .label("Username")
            .description("Authentication username")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .placeholder("guest")
            .group("Authentication")
            .order(4)
            .build(),
        ConfigField.builder()
            .name("password")
            .label("Password")
            .description("Authentication password")
            .type(ConfigField.FieldType.PASSWORD)
            .required(true)
            .group("Authentication")
            .order(5)
            .build(),
        ConfigField.builder()
            .name("use_ssl")
            .label("Use SSL/TLS")
            .description("Enable SSL/TLS connection")
            .type(ConfigField.FieldType.BOOLEAN)
            .required(false)
            .defaultValue(false)
            .group("Security")
            .order(6)
            .build(),
        ConfigField.builder()
            .name("exchange")
            .label("Exchange")
            .description("Default exchange for publishing")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Messaging")
            .order(7)
            .build(),
        ConfigField.builder()
            .name("routing_key")
            .label("Routing Key")
            .description("Default routing key for publishing")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Messaging")
            .order(8)
            .build(),
        ConfigField.builder()
            .name("prefetch_count")
            .label("Prefetch Count")
            .description("Number of messages to prefetch")
            .type(ConfigField.FieldType.NUMBER)
            .required(false)
            .defaultValue(10)
            .group("Consumer")
            .order(9)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();

    if (config.get("host") == null || ((String) config.get("host")).isBlank()) {
      errors.put("host", "Host is required");
    }
    if (config.get("username") == null || ((String) config.get("username")).isBlank()) {
      errors.put("username", "Username is required");
    }
    if (config.get("password") == null) {
      errors.put("password", "Password is required");
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();

    try {
      ConnectionFactory factory = createConnectionFactory(config);
      try (Connection connection = factory.newConnection()) {
        Channel channel = connection.createChannel();
        int queueCount = 0;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("server_version", connection.getServerProperties().get("version"));
        metadata.put("cluster_name", connection.getServerProperties().get("cluster_name"));

        channel.close();

        return new TestResult(
            true,
            "Successfully connected to RabbitMQ",
            System.currentTimeMillis() - start,
            metadata);
      }
    } catch (IOException | TimeoutException e) {
      return new TestResult(
          false,
          "Connection failed: " + e.getMessage(),
          System.currentTimeMillis() - start,
          null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();

    try {
      ConnectionFactory factory = createConnectionFactory(config);
      try (Connection connection = factory.newConnection()) {
        @SuppressWarnings("unused")
        Channel channel = connection.createChannel();
        // RabbitMQ doesn't have a direct API to list queues; this would require management API
        // For now, return a placeholder indicating direct queue access
        streams.add(
            new StreamInfo(
                "_direct_queue_access",
                null,
                List.of(
                    new FieldInfo("body", "string", false, "Message body"),
                    new FieldInfo("routing_key", "string", true, "Routing key"),
                    new FieldInfo("exchange", "string", true, "Source exchange"),
                    new FieldInfo("delivery_tag", "long", false, "Delivery tag"),
                    new FieldInfo("timestamp", "long", true, "Message timestamp")),
                List.of(),
                Map.of(
                    "note",
                    "Specify queue name directly when reading. Use RabbitMQ Management API for queue listing.")));
      }
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException("Failed to connect: " + e.getMessage(), e);
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String queueName, ReadOptions options) {
    return new RabbitMqRecordIterator(config, queueName, options);
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String routingKey, List<Record> records, WriteOptions writeOptions) {
    long start = System.currentTimeMillis();
    int written = 0;

    try {
      ConnectionFactory factory = createConnectionFactory(config);
      try (Connection connection = factory.newConnection();
          Channel channel = connection.createChannel()) {

        String exchange = (String) config.getOrDefault("exchange", "");

        for (Record record : records) {
          String messageBody;
          if (record.data().containsKey("body")) {
            messageBody = String.valueOf(record.data().get("body"));
          } else {
            messageBody = objectMapper.writeValueAsString(record.data());
          }

          String key =
              record.data().containsKey("routing_key")
                  ? String.valueOf(record.data().get("routing_key"))
                  : routingKey;

          channel.basicPublish(exchange, key, null, messageBody.getBytes(StandardCharsets.UTF_8));
          written++;
        }
      }
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException("Failed to write to RabbitMQ: " + e.getMessage(), e);
    }

    return new WriteResult(
        written, 0, System.currentTimeMillis() - start, Map.of("routing_key", routingKey));
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // RabbitMQ is schema-less; queues and exchanges are created as needed
  }

  private ConnectionFactory createConnectionFactory(Map<String, Object> config) {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost((String) config.get("host"));
    factory.setPort(((Number) config.getOrDefault("port", 5672)).intValue());
    factory.setVirtualHost((String) config.getOrDefault("virtual_host", "/"));
    factory.setUsername((String) config.get("username"));
    factory.setPassword((String) config.get("password"));

    Boolean useSsl = (Boolean) config.getOrDefault("use_ssl", false);
    if (Boolean.TRUE.equals(useSsl)) {
      try {
        factory.useSslProtocol();
      } catch (Exception e) {
        throw new RuntimeException("Failed to configure SSL: " + e.getMessage(), e);
      }
    }

    factory.setConnectionTimeout(5000);
    return factory;
  }

  private class RabbitMqRecordIterator implements RecordIterator {
    private final Connection connection;
    private final Channel channel;
    private final String queueName;
    private final int batchSize;
    private final Queue<GetResponse> buffer = new LinkedList<>();
    private boolean closed = false;
    private long lastDeliveryTag = -1;

    RabbitMqRecordIterator(Map<String, Object> config, String queueName, ReadOptions options) {
      this.queueName = queueName;
      this.batchSize = options != null ? options.batchSize() : 10;

      try {
        ConnectionFactory factory = createConnectionFactory(config);
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        int prefetch = ((Number) config.getOrDefault("prefetch_count", 10)).intValue();
        channel.basicQos(prefetch);

        fetchBatch();
      } catch (IOException | TimeoutException e) {
        throw new RuntimeException("Failed to connect: " + e.getMessage(), e);
      }
    }

    private void fetchBatch() {
      try {
        for (int i = 0; i < batchSize; i++) {
          GetResponse response = channel.basicGet(queueName, true);
          if (response == null) {
            break;
          }
          buffer.add(response);
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to fetch messages: " + e.getMessage(), e);
      }
    }

    @Override
    public boolean hasNext() {
      if (closed) {
        return false;
      }
      if (buffer.isEmpty()) {
        fetchBatch();
      }
      return !buffer.isEmpty();
    }

    @Override
    public Record next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      GetResponse response = buffer.poll();
      if (response == null) {
        throw new NoSuchElementException();
      }

      lastDeliveryTag = response.getEnvelope().getDeliveryTag();

      Map<String, Object> data = new HashMap<>();
      data.put("body", new String(response.getBody(), StandardCharsets.UTF_8));
      data.put("routing_key", response.getEnvelope().getRoutingKey());
      data.put("exchange", response.getEnvelope().getExchange());
      data.put("delivery_tag", lastDeliveryTag);

      if (response.getProps() != null && response.getProps().getTimestamp() != null) {
        data.put("timestamp", response.getProps().getTimestamp().getTime());
      }

      return new Record(
          data,
          String.valueOf(lastDeliveryTag),
          response.getProps() != null && response.getProps().getTimestamp() != null
              ? response.getProps().getTimestamp().getTime()
              : System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return String.valueOf(lastDeliveryTag);
    }

    @Override
    public void close() {
      closed = true;
      try {
        if (channel != null && channel.isOpen()) {
          channel.close();
        }
        if (connection != null && connection.isOpen()) {
          connection.close();
        }
      } catch (Exception ignored) {
        // Ignore close errors
      }
    }
  }
}
