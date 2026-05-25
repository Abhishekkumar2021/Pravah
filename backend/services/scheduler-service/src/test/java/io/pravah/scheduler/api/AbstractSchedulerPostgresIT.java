package io.pravah.scheduler.api;

import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.containers.RedisContainerExtension;
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

@ExtendWith({PostgresContainerExtension.class, RedisContainerExtension.class})
@TestPropertySource(
    properties = {
      "pravah.scheduler.evaluation-interval-ms=999999999",
      "pravah.scheduler.leader-lock-ttl-seconds=999999999"
    })
public abstract class AbstractSchedulerPostgresIT {

  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
    registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
    registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
  }

  @BeforeEach
  void truncateSchedulerTables() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      // DELETE avoids TRUNCATE AccessExclusiveLock deadlocks with concurrent test workers.
      statement.execute("DELETE FROM trigger_dispatch_history");
      statement.execute("DELETE FROM trigger_dispatch_pending");
      statement.execute("DELETE FROM schedule_history");
      statement.execute("DELETE FROM schedules");
      statement.execute("DELETE FROM scheduler_locks");
    }
  }
}
