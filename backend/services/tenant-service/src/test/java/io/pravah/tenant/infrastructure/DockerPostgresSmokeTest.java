package io.pravah.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies Docker (e.g. OrbStack) is reachable and Testcontainers can start PostgreSQL.
 *
 * <p>When no Docker daemon is available the test is skipped ({@code disabledWithoutDocker}). With
 * OrbStack or Docker Desktop running, it executes and validates JDBC connectivity to a real
 * PostgreSQL container.
 */
@Testcontainers(disabledWithoutDocker = true)
class DockerPostgresSmokeTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("tc")
          .withUsername("tc")
          .withPassword("tc");

  @Test
  void postgresContainerAcceptsJdbcConnections() throws Exception {
    try (var conn =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT 1 AS ok")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt("ok")).isEqualTo(1);
    }
  }
}
