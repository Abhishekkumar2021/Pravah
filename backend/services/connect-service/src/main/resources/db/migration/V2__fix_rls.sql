-- Fix RLS: use pravah.current_tenant_id (matches RlsAspect), add WITH CHECK and FORCE

DROP POLICY IF EXISTS connections_tenant_isolation ON connections;
DROP POLICY IF EXISTS sync_jobs_tenant_isolation ON sync_jobs;

CREATE POLICY connections_tenant_isolation ON connections
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid);

CREATE POLICY sync_jobs_tenant_isolation ON sync_jobs
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid);

ALTER TABLE connections FORCE ROW LEVEL SECURITY;
ALTER TABLE sync_jobs FORCE ROW LEVEL SECURITY;
