package io.pravah.playground.pg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RlsAndPartitioningIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("playground")
            .withUsername("playground")
            .withPassword("playground");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        String url = "jdbc:postgresql://%s:%s/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getDatabaseName());
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> "app_tenant");
        r.add("spring.datasource.password", () -> "playground");
        r.add("spring.flyway.url", () -> url);
        r.add("spring.flyway.user", () -> "playground");
        r.add("spring.flyway.password", () -> "playground");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TenantScopedJdbc tenantScoped;

    @Test
    void rlsIsolatesTenants() {
        UUID acmeJob = tenantScoped.insertJob("acme", "job-a");
        UUID betaJob = tenantScoped.insertJob("beta", "job-b");

        assertThat(acmeJob).isNotNull();
        assertThat(betaJob).isNotNull();

        List<Map<String, Object>> acmeRows = tenantScoped.listJobs("acme");
        assertThat(acmeRows).hasSize(1);
        assertThat(acmeRows.get(0).get("tenant_id")).isEqualTo("acme");

        List<Map<String, Object>> betaRows = tenantScoped.listJobs("beta");
        assertThat(betaRows).hasSize(1);
        assertThat(betaRows.get(0).get("tenant_id")).isEqualTo("beta");
    }

    @Test
    void partitionRouting_insertsLandInExpectedChild() {
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, created_at, payload)
                VALUES ('acme', ?, ?::jsonb)
                """,
                OffsetDateTime.parse("2026-05-15T12:00:00Z"),
                "{\"k\":1}");

        jdbc.update("""
                INSERT INTO audit_events (tenant_id, created_at, payload)
                VALUES ('acme', now(), ?::jsonb)
                """,
                "{\"k\":2}");

        Long inMay = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events_2026_05",
                Long.class);
        Long inDefault = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events_default",
                Long.class);

        assertThat(inMay).isEqualTo(1);
        assertThat(inDefault).isEqualTo(1);
    }
}
