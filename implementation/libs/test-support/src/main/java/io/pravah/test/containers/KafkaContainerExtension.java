package io.pravah.test.containers;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension that provides a shared Kafka container for integration tests.
 *
 * <p>Uses the Confluent Kafka image for reliability. Implements {@link CloseableResource} for
 * proper cleanup via JUnit's extension store.
 *
 * <pre>
 * &#64;ExtendWith(KafkaContainerExtension.class)
 * class MyKafkaIntegrationTest {
 *     // Use KafkaContainerExtension.getBootstrapServers()
 * }
 * </pre>
 */
public class KafkaContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(KafkaContainerExtension.class);
  private static final String CONTAINER_KEY = "kafka-container";

  private static final KafkaContainer KAFKA;

  static {
    KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withReuse(true);
    KAFKA.start();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    context
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            CONTAINER_KEY, key -> new KafkaContainerResource(KAFKA), KafkaContainerResource.class);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Container cleanup is handled via CloseableResource when the test context closes
  }

  public static String getBootstrapServers() {
    return KAFKA.getBootstrapServers();
  }

  public static KafkaContainer getContainer() {
    return KAFKA;
  }

  /**
   * Wrapper that implements CloseableResource for proper JUnit cleanup.
   *
   * <p>When the root extension context closes (all tests complete), this will stop the container if
   * reuse is not enabled.
   */
  private static class KafkaContainerResource implements CloseableResource {
    private final KafkaContainer container;

    KafkaContainerResource(KafkaContainer container) {
      this.container = container;
    }

    @Override
    public void close() {
      // Only stop if not using reuse (Testcontainers manages reusable containers)
      if (!container.isShouldBeReused() && container.isRunning()) {
        container.stop();
      }
    }
  }
}
