package io.pravah.test.containers;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JUnit 5 extension that provides a shared PostgreSQL container for integration tests.
 *
 * <p>The container is started once per test class and reused across all test methods. Uses
 * singleton pattern for efficiency when multiple test classes need PostgreSQL.
 *
 * <p>Implements {@link CloseableResource} for proper cleanup via JUnit's extension store.
 *
 * <pre>
 * &#64;ExtendWith(PostgresContainerExtension.class)
 * class MyIntegrationTest {
 *     // Use PostgresContainerExtension.getJdbcUrl() etc.
 * }
 * </pre>
 */
public class PostgresContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(PostgresContainerExtension.class);
  private static final String CONTAINER_KEY = "postgres-container";

  private static final PostgreSQLContainer<?> POSTGRES;

  static {
    POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pravah_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
    POSTGRES.start();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    context
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            CONTAINER_KEY,
            key -> new PostgresContainerResource(POSTGRES),
            PostgresContainerResource.class);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Container cleanup is handled via CloseableResource when the test context closes
  }

  public static String getJdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  public static String getUsername() {
    return POSTGRES.getUsername();
  }

  public static String getPassword() {
    return POSTGRES.getPassword();
  }

  public static String getHost() {
    return POSTGRES.getHost();
  }

  public static int getPort() {
    return POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
  }

  public static PostgreSQLContainer<?> getContainer() {
    return POSTGRES;
  }

  /**
   * Wrapper that implements CloseableResource for proper JUnit cleanup.
   *
   * <p>When the root extension context closes (all tests complete), this will stop the container if
   * reuse is not enabled.
   */
  private static class PostgresContainerResource implements CloseableResource {
    private final PostgreSQLContainer<?> container;

    PostgresContainerResource(PostgreSQLContainer<?> container) {
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
