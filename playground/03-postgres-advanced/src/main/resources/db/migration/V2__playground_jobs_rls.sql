-- ADR-013: Row-Level Security with session variable pravah.current_tenant_id
-- FORCE applies policies even to the table owner (app_tenant is not superuser).

SET ROLE app_tenant;

CREATE TABLE playground_jobs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  TEXT NOT NULL,
    name       TEXT NOT NULL,
    status     TEXT NOT NULL DEFAULT 'pending'
);

ALTER TABLE playground_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE playground_jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON playground_jobs
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true))
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true));

RESET ROLE;
