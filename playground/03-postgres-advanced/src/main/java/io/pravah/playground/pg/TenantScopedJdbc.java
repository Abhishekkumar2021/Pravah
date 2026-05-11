package io.pravah.playground.pg;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal JDBC helper showing ADR-013 pattern: {@code SET LOCAL pravah.current_tenant_id}
 * at the start of each transaction so RLS policies apply.
 */
@Component
public class TenantScopedJdbc {

    private static final String SET_TENANT = "SET LOCAL pravah.current_tenant_id = ?";

    private final JdbcTemplate jdbc;

    public TenantScopedJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID insertJob(String tenantId, String name) {
        jdbc.update(SET_TENANT, tenantId);
        return jdbc.queryForObject(
                """
                        INSERT INTO playground_jobs (tenant_id, name)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId,
                name);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(String tenantId) {
        jdbc.update(SET_TENANT, tenantId);
        return jdbc.queryForList("SELECT id, tenant_id, name, status FROM playground_jobs ORDER BY name");
    }
}
