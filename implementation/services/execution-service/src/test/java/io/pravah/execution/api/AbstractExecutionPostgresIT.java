package io.pravah.execution.api;

import io.pravah.test.containers.PostgresContainerExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared PostgreSQL container for execution-service API integration tests.
 *
 * <p>Uses {@link PostgresContainerExtension} so one container stays up for the whole JVM test run.
 */
@ExtendWith(PostgresContainerExtension.class)
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
      "pravah.outbox.relay.enabled=false",
      "pravah.kafka.execution-created-listener-enabled=false",
      "pravah.kafka.job-worker-listener-enabled=false"
    })
public abstract class AbstractExecutionPostgresIT {

  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @BeforeEach
  void truncateExecutionTables() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE TABLE processed_events; TRUNCATE TABLE outbox; TRUNCATE TABLE executions CASCADE;");
    }
  }
}
