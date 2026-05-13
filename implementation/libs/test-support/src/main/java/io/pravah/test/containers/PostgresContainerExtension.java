package io.pravah.test.containers;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JUnit 5 extension that provides a shared PostgreSQL container for integration tests.
 * <p>
 * The container is started once per test class and reused across all test methods.
 * Uses singleton pattern for efficiency when multiple test classes need PostgreSQL.
 *
 * <pre>
 * &#64;ExtendWith(PostgresContainerExtension.class)
 * class MyIntegrationTest {
 *     // Use PostgresContainerExtension.getJdbcUrl() etc.
 * }
 * </pre>
 */
public class PostgresContainerExtension implements BeforeAllCallback {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pravah_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
        POSTGRES.start();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // Container is already started statically
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
}
