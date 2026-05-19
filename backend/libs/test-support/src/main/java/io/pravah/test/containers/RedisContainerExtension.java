package io.pravah.test.containers;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** JUnit 5 extension providing a shared Redis container for integration tests (ADR-012). */
public class RedisContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(RedisContainerExtension.class);
  private static final String CONTAINER_KEY = "redis-container";

  private static final GenericContainer<?> REDIS;

  static {
    REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);
    REDIS.start();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    context
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            CONTAINER_KEY, key -> new RedisContainerResource(REDIS), RedisContainerResource.class);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Cleanup via CloseableResource when root store closes
  }

  public static String getHost() {
    return REDIS.getHost();
  }

  public static int getPort() {
    return REDIS.getMappedPort(6379);
  }

  private record RedisContainerResource(GenericContainer<?> container)
      implements CloseableResource {
    @Override
    public void close() {
      // Reusable container; Testcontainers manages lifecycle when reuse is enabled
    }
  }
}
