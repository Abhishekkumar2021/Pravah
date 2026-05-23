package io.pravah.pipeline.api;

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
 * Shared PostgreSQL container and datasource properties for pipeline-service API integration tests.
 *
 * <p>Uses {@link PostgresContainerExtension} so one container stays up for the whole JVM test run.
 * The JUnit {@code @Container static} pattern on a shared base class can stop Postgres after the
 * first test class, breaking later classes that reuse or refresh the Spring context.
 */
@ExtendWith(PostgresContainerExtension.class)
@TestPropertySource(
    properties = {
      "pravah.outbox.relay.enabled=false",
      "pravah.security.allow-private-network-targets=true",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    })
public abstract class AbstractPipelinePostgresIT {

  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @BeforeEach
  void truncatePipelineTables() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE TABLE outbox, pipeline_versions, pipeline_events, pipelines, connections, tenant_secrets"
              + " CASCADE;");
    }
  }
}
