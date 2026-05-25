-- V3: Add RLS to sessions table (code review remediation)
-- Sessions must be tenant-isolated via user's tenant to prevent cross-tenant session leakage.

ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_sessions ON sessions
    FOR ALL
    USING (user_id IN (
        SELECT id FROM users WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ))
    WITH CHECK (user_id IN (
        SELECT id FROM users WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ));
