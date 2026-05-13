package io.pravah.test.containers;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension that provides a shared Kafka container for integration tests.
 *
 * <p>Uses the Confluent Kafka image for reliability.
 *
 * <pre>
 * &#64;ExtendWith(KafkaContainerExtension.class)
 * class MyKafkaIntegrationTest {
 *     // Use KafkaContainerExtension.getBootstrapServers()
 * }
 * </pre>
 */
public class KafkaContainerExtension implements BeforeAllCallback {

  private static final KafkaContainer KAFKA;

  static {
    KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withReuse(true);
    KAFKA.start();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    // Container is already started statically
  }

  public static String getBootstrapServers() {
    return KAFKA.getBootstrapServers();
  }

  public static KafkaContainer getContainer() {
    return KAFKA;
  }
}
